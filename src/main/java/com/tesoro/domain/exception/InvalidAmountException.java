package com.tesoro.domain.exception;

public class InvalidAmountException extends DomainException {

    public InvalidAmountException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "invalid-amount";
    }
}
