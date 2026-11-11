package com.tesoro.domain.model;

/** Supported currencies. Both use two decimal places (centavos / cents). */
public enum CurrencyCode {
    ARS(2),
    USD(2);

    private final int scale;

    CurrencyCode(int scale) {
        this.scale = scale;
    }

    /** Number of decimal places allowed for amounts in this currency. */
    public int scale() {
        return scale;
    }
}
