package com.tesoro.domain.exception;

import com.tesoro.domain.model.Money;
import java.util.UUID;

public class InsufficientFundsException extends DomainException {

    private final UUID accountId;

    public InsufficientFundsException(UUID accountId, Money available, Money requested) {
        super("Saldo insuficiente: disponible %s, solicitado %s".formatted(available, requested));
        this.accountId = accountId;
    }

    public UUID accountId() {
        return accountId;
    }

    @Override
    public String code() {
        return "insufficient-funds";
    }
}
