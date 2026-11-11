package com.tesoro.domain.exception;

public class SameAccountTransferException extends DomainException {

    public SameAccountTransferException() {
        super("No se puede transferir a la misma cuenta");
    }

    @Override
    public String code() {
        return "same-account-transfer";
    }
}
