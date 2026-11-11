package com.tesoro.application.service;

import com.tesoro.application.command.Fingerprints;
import com.tesoro.application.command.RegisterCommand;
import com.tesoro.application.exception.EmailAlreadyRegisteredException;
import com.tesoro.application.exception.InvalidCredentialsException;
import com.tesoro.application.exception.InvalidRefreshTokenException;
import com.tesoro.application.exception.NotFoundException;
import com.tesoro.application.model.AuditEntry;
import com.tesoro.application.model.AuthTokens;
import com.tesoro.application.model.IssuedToken;
import com.tesoro.application.model.RefreshToken;
import com.tesoro.application.port.in.AuthUseCase;
import com.tesoro.application.port.out.AccessTokenIssuer;
import com.tesoro.application.port.out.AccountRepository;
import com.tesoro.application.port.out.AuditLog;
import com.tesoro.application.port.out.OutboxRepository;
import com.tesoro.application.port.out.PasswordHasher;
import com.tesoro.application.port.out.RefreshTokenRepository;
import com.tesoro.application.port.out.UserRepository;
import com.tesoro.domain.event.UserRegistered;
import com.tesoro.domain.model.Account;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Role;
import com.tesoro.domain.model.User;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements AuthUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final AccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final AccessTokenIssuer accessTokens;
    private final AuditLog auditLog;
    private final OutboxRepository outbox;
    private final Clock clock;
    private final Duration refreshTokenTtl;
    /** Compared against when the email does not exist, so both paths take the same time. */
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            AccountRepository accounts,
            RefreshTokenRepository refreshTokens,
            PasswordHasher passwordHasher,
            AccessTokenIssuer accessTokens,
            AuditLog auditLog,
            OutboxRepository outbox,
            Clock clock,
            @Value("${tesoro.security.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.users = users;
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.passwordHasher = passwordHasher;
        this.accessTokens = accessTokens;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
        this.dummyHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public AuthTokens register(RegisterCommand command) {
        String email = User.normalizeEmail(command.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException("Ya existe una cuenta con ese email");
        }
        Instant now = clock.instant();
        User user = User.register(email, passwordHasher.hash(command.password()), command.fullName(), Role.USER, now);
        users.insert(user);
        for (CurrencyCode currency : CurrencyCode.values()) {
            accounts.insert(Account.openWallet(user.id(), currency, now));
        }
        outbox.enqueue(new UserRegistered(user.id(), user.email(), now));
        auditLog.record(AuditEntry.of(now, user.id(), "USER_REGISTERED", "User", user.id(), Map.of()));
        return issueTokens(user, UUID.randomUUID(), now);
    }

    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthTokens login(String email, String password) {
        Instant now = clock.instant();
        Optional<User> found = users.findByEmail(User.normalizeEmail(email));
        String hash = found.map(User::passwordHash).orElse(dummyHash);
        boolean matches = passwordHasher.matches(password, hash);
        if (found.isEmpty() || !matches || !found.get().enabled()) {
            auditLog.record(AuditEntry.of(
                    now,
                    found.map(User::id).orElse(null),
                    "LOGIN_FAILED",
                    "User",
                    null,
                    Map.of("email", User.normalizeEmail(email))));
            throw new InvalidCredentialsException("Email o contraseña incorrectos");
        }
        User user = found.get();
        auditLog.record(AuditEntry.of(now, user.id(), "LOGIN_SUCCEEDED", "User", user.id(), Map.of()));
        return issueTokens(user, UUID.randomUUID(), now);
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokens refresh(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens
                .findByHash(Fingerprints.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token inválido"));

        // Rotation: a token can be used once. If the same token shows up again, someone else may
        // hold a copy, so the whole family (every token from that login) is revoked.
        if (token.isRevoked() || !refreshTokens.revoke(token.id(), now)) {
            refreshTokens.revokeFamily(token.familyId(), now);
            auditLog.record(AuditEntry.of(
                    now, token.userId(), "REFRESH_TOKEN_REUSED", "RefreshTokenFamily", token.familyId(), Map.of()));
            throw new InvalidRefreshTokenException("Refresh token reutilizado: la sesión fue cerrada");
        }
        if (token.isExpired(now)) {
            throw new InvalidRefreshTokenException("Refresh token vencido");
        }
        User user = users.findById(token.userId())
                .filter(User::enabled)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token inválido"));
        return issueTokens(user, token.familyId(), now);
    }

    @Override
    @Transactional
    public void logout(String rawToken) {
        Instant now = clock.instant();
        refreshTokens.findByHash(Fingerprints.sha256Hex(rawToken)).ifPresent(token -> {
            refreshTokens.revokeFamily(token.familyId(), now);
            auditLog.record(AuditEntry.of(now, token.userId(), "LOGOUT", "User", token.userId(), Map.of()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public User currentUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private AuthTokens issueTokens(User user, UUID familyId, Instant now) {
        IssuedToken access = accessTokens.issue(user);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        refreshTokens.insert(new RefreshToken(
                UUID.randomUUID(),
                user.id(),
                familyId,
                Fingerprints.sha256Hex(rawRefresh),
                refreshExpiresAt,
                null,
                now));
        return new AuthTokens(access.value(), access.expiresAt(), rawRefresh, refreshExpiresAt);
    }
}
