package com.tesoro.domain.exception;

/** Base class for business rule violations. {@link #code()} is a stable, machine-readable id. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    public abstract String code();
}
