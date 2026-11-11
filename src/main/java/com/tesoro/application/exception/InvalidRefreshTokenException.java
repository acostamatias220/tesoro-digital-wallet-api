package com.tesoro.application.exception;

public class InvalidRefreshTokenException extends ApplicationException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "invalid-refresh-token";
    }
}
