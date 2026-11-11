package com.tesoro.domain.exception;

import java.util.UUID;

public class AccountFrozenException extends DomainException {

    public AccountFrozenException(UUID accountId) {
        super("La cuenta %s está congelada y no admite movimientos".formatted(accountId));
    }

    @Override
    public String code() {
        return "account-frozen";
    }
}
