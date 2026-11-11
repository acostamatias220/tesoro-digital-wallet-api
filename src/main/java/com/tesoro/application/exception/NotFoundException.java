package com.tesoro.application.exception;

public class NotFoundException extends ApplicationException {

    public NotFoundException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "not-found";
    }
}
