package com.tesoro.application.service;

import com.tesoro.application.exception.NotFoundException;
import com.tesoro.application.model.AuditEntry;
import com.tesoro.application.model.OutboxMessage;
import com.tesoro.application.model.SystemAccountBalance;
import com.tesoro.application.model.UserSummary;
import com.tesoro.application.port.in.AdminUseCase;
import com.tesoro.application.port.out.AccountRepository;
import com.tesoro.application.port.out.AuditLog;
import com.tesoro.application.port.out.LedgerRepository;
import com.tesoro.application.port.out.OutboxRepository;
import com.tesoro.application.port.out.UserRepository;
import com.tesoro.domain.model.Account;
import com.tesoro.domain.model.AccountKind;
import com.tesoro.domain.model.AccountStatus;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminService implements AdminUseCase {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final AuditLog auditLog;
    private final OutboxRepository outbox;
    private final Clock clock;

    public AdminService(
            UserRepository users,
            AccountRepository accounts,
            LedgerRepository ledger,
            AuditLog auditLog,
            OutboxRepository outbox,
            Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.ledger = ledger;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public Page<UserSummary> users(Pageable pageable) {
        return users.findAll(pageable)
                .map(u -> new UserSummary(u.id(), u.email(), u.fullName(), u.role(), u.enabled(), u.createdAt()));
    }

    @Override
    public List<Account> accountsOf(UUID userId) {
        users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return accounts.findByOwner(userId);
    }

    @Override
    @Transactional
    public Account changeAccountStatus(UUID adminId, UUID accountId, AccountStatus status, String reason) {
        // Same lock a movement takes, so the status change waits for in-flight movements.
        Account account = accounts.lockForUpdate(accountId);
        if (account.kind() != AccountKind.USER_WALLET) {
            throw new NotFoundException("Cuenta no encontrada");
        }
        AccountStatus previous = account.status();
        if (status == AccountStatus.FROZEN) {
            account.freeze();
        } else {
            account.activate();
        }
        accounts.update(account);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", previous.name());
        details.put("to", status.name());
        details.put("reason", reason == null ? "" : reason);
        auditLog.record(
                AuditEntry.of(clock.instant(), adminId, "ACCOUNT_STATUS_CHANGED", "Account", accountId, details));
        return account;
    }

    @Override
    public List<SystemAccountBalance> systemBalances() {
        return ledger.systemBalances();
    }

    @Override
    public Page<AuditEntry> auditLog(String action, Pageable pageable) {
        return auditLog.find(action, pageable);
    }

    @Override
    public Page<OutboxMessage> outbox(Pageable pageable) {
        return outbox.findRecent(pageable);
    }
}
