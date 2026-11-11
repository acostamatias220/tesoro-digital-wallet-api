package com.tesoro.domain.exception;

import com.tesoro.domain.model.CurrencyCode;

public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(CurrencyCode expected, CurrencyCode actual) {
        super("Moneda incompatible: se esperaba %s y se recibió %s".formatted(expected, actual));
    }

    @Override
    public String code() {
        return "currency-mismatch";
    }
}
