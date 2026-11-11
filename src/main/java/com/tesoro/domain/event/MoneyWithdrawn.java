package com.tesoro.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MoneyWithdrawn(UUID entryId, UUID accountId, BigDecimal amount, String currency, Instant occurredAt)
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
