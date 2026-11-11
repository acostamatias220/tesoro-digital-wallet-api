package com.tesoro.application.model;

import com.tesoro.domain.ledger.EntryType;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One line of an account statement. */
public record AccountMovement(
        long postingId,
        UUID transactionId,
        EntryType type,
        Direction direction,
        BigDecimal amount,
        CurrencyCode currency,
        BigDecimal balanceAfter,
        String description,
        Instant createdAt) {}
