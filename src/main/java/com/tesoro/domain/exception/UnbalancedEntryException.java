package com.tesoro.domain.exception;

public class UnbalancedEntryException extends DomainException {

    public UnbalancedEntryException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "unbalanced-entry";
    }
}
