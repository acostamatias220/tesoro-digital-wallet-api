package com.tesoro.application.exception;

public class ExchangeRateUnavailableException extends ApplicationException {

    public ExchangeRateUnavailableException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "exchange-rate-unavailable";
    }
}
