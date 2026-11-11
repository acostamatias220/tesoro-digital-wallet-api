package com.tesoro.application.command;

import com.tesoro.domain.model.Money;
import java.util.UUID;

/** Simulated incoming bank transfer (in production this would come from a PSP webhook). */
public record DepositCommand(UUID userId, String idempotencyKey, Money amount) {

    public String fingerprint() {
        return Fingerprints.of("deposit", amount.currency(), amount.amount().toPlainString());
    }
}
