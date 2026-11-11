package com.tesoro.application.exception;

public class InvalidCredentialsException extends ApplicationException {

    public InvalidCredentialsException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "invalid-credentials";
    }
}
