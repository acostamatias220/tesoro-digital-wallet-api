package com.tesoro.application.exception;

public class IdempotencyKeyReusedException extends ApplicationException {

    public IdempotencyKeyReusedException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "idempotency-key-reused";
    }
}
