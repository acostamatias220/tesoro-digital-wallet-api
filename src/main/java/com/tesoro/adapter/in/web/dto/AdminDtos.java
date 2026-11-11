package com.tesoro.adapter.in.web.dto;

import com.tesoro.application.model.ReconciliationIssue;
import com.tesoro.domain.model.AccountKind;
import com.tesoro.domain.model.AccountStatus;
import com.tesoro.domain.model.CurrencyCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {}

    public record UserResponse(
            UUID id, String email, String fullName, String role, boolean enabled, Instant createdAt) {}

    public record AccountStatusRequest(
            @NotNull AccountStatus status, @Size(max = 200) String reason) {}

    public record SystemAccountResponse(
            UUID accountId, String code, AccountKind kind, CurrencyCode currency, BigDecimal balance) {}

    public record ReconciliationResponse(
            UUID id,
            String trigger,
            String status,
            long walletsChecked,
            long entriesChecked,
            List<ReconciliationIssue> issues,
            Instant startedAt,
            Instant finishedAt) {}

    public record AuditEntryResponse(
            Long id,
            Instant occurredAt,
            UUID actorId,
            String action,
            String targetType,
            String targetId,
            String ipAddress,
            Map<String, Object> details) {}

    public record OutboxMessageResponse(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant createdAt,
            Instant publishedAt,
            int attempts,
            String lastError) {}
}
