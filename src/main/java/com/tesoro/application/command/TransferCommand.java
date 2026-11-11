package com.tesoro.application.command;

import com.tesoro.domain.model.Money;
import java.util.UUID;

public record TransferCommand(
        UUID userId, String idempotencyKey, String recipientEmail, Money amount, String description) {

    public String fingerprint() {
        return Fingerprints.of(
                "transfer",
                recipientEmail.strip().toLowerCase(java.util.Locale.ROOT),
                amount.currency(),
                amount.amount().toPlainString(),
                description);
    }
}
