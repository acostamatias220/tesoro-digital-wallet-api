package com.tesoro.application.command;

import com.tesoro.domain.model.Money;
import java.util.UUID;

public record WithdrawCommand(UUID userId, String idempotencyKey, Money amount) {

    public String fingerprint() {
        return Fingerprints.of("withdrawal", amount.currency(), amount.amount().toPlainString());
    }
}
