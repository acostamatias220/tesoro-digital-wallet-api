package com.tesoro.application.port.in;

import com.tesoro.application.model.AuditEntry;
import com.tesoro.application.model.OutboxMessage;
import com.tesoro.application.model.SystemAccountBalance;
import com.tesoro.application.model.UserSummary;
import com.tesoro.domain.model.Account;
import com.tesoro.domain.model.AccountStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUseCase {

    Page<UserSummary> users(Pageable pageable);

    List<Account> accountsOf(UUID userId);

    Account changeAccountStatus(UUID adminId, UUID accountId, AccountStatus status, String reason);

    List<SystemAccountBalance> systemBalances();

    Page<AuditEntry> auditLog(String action, Pageable pageable);

    Page<OutboxMessage> outbox(Pageable pageable);
}
