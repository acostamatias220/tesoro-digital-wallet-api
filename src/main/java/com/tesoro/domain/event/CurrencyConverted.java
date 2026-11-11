package com.tesoro.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CurrencyConverted(
        UUID entryId,
        UUID userId,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal targetAmount,
        String targetCurrency,
        BigDecimal rate,
        Instant occurredAt)
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
