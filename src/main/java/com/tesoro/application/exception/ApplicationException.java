package com.tesoro.application.exception;

/** Base class for use case errors that are not domain rule violations. */
public abstract class ApplicationException extends RuntimeException {

    protected ApplicationException(String message) {
        super(message);
    }

    public abstract String code();
}
