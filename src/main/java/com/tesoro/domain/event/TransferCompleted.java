package com.tesoro.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferCompleted(
        UUID entryId, UUID fromAccountId, UUID toAccountId, BigDecimal amount, String currency, Instant occurredAt)
        implements DomainEvent {

    @Override
    public String aggregateType() {
        return "JournalEntry";
    }

    @Override
    public UUID aggregateId() {
        return entryId;
    }
}
