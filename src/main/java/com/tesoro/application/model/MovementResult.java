package com.tesoro.application.model;

import com.tesoro.domain.ledger.EntryType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outcome of a money movement as seen by the user who requested it. This is also the payload that
 * is stored with the idempotency key and replayed verbatim on retries.
 */
public record MovementResult(
        UUID transactionId,
        EntryType type,
        String description,
        Instant createdAt,
        List<WalletChange> walletChanges,
        Map<String, String> details) {}
