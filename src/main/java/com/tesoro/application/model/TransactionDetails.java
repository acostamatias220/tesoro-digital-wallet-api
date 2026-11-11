package com.tesoro.application.model;

import com.tesoro.domain.ledger.EntryType;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A journal entry with all its postings. */
public record TransactionDetails(
        UUID id,
        EntryType type,
        String description,
        UUID initiatedBy,
        Map<String, String> metadata,
        List<Line> postings,
        Instant createdAt) {

    public record Line(
            UUID accountId, String accountLabel, CurrencyCode currency, Direction direction, BigDecimal amount) {}
}
