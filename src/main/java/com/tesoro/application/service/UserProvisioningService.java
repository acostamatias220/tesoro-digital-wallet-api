package com.tesoro.application.service;

import com.tesoro.application.model.AuditEntry;
import com.tesoro.application.port.out.AccountRepository;
import com.tesoro.application.port.out.AuditLog;
import com.tesoro.application.port.out.PasswordHasher;
import com.tesoro.application.port.out.UserRepository;
import com.tesoro.domain.model.Account;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Role;
import com.tesoro.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates users outside the public sign-up flow (bootstrap admin, demo data). */
@Service
public class UserProvisioningService {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final AuditLog auditLog;
    private final Clock clock;

    public UserProvisioningService(
            UserRepository users,
            AccountRepository accounts,
            PasswordHasher passwordHasher,
            AuditLog auditLog,
            Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /** Creates the user with ARS and USD wallets unless the email already exists. */
    @Transactional
    public boolean ensureUser(String email, String rawPassword, String fullName, Role role) {
        if (users.existsByEmail(User.normalizeEmail(email))) {
            return false;
        }
        Instant now = clock.instant();
        User user = User.register(email, passwordHasher.hash(rawPassword), fullName, role, now);
        users.insert(user);
        for (CurrencyCode currency : CurrencyCode.values()) {
            accounts.insert(Account.openWallet(user.id(), currency, now));
        }
        auditLog.record(AuditEntry.of(now, null, "USER_PROVISIONED", "User", user.id(), Map.of("role", role.name())));
        return true;
    }
}
