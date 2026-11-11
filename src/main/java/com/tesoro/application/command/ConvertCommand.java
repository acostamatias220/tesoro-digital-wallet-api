package com.tesoro.application.command;

import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Money;
import java.util.UUID;

public record ConvertCommand(UUID userId, String idempotencyKey, Money amount, CurrencyCode targetCurrency) {

    public String fingerprint() {
        return Fingerprints.of("conversion", amount.currency(), amount.amount().toPlainString(), targetCurrency);
    }
}
