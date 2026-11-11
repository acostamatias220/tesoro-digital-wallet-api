package com.tesoro.application.exception;

public class EmailAlreadyRegisteredException extends ApplicationException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "email-already-registered";
    }
}
