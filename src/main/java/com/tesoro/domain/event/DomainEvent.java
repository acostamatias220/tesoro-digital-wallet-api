package com.tesoro.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Facts published through the transactional outbox after a successful operation. */
public sealed interface DomainEvent
        permits UserRegistered, MoneyDeposited, MoneyWithdrawn, TransferCompleted, CurrencyConverted {

    String aggregateType();

    UUID aggregateId();

    Instant occurredAt();

    default String eventType() {
        return getClass().getSimpleName();
    }
}
