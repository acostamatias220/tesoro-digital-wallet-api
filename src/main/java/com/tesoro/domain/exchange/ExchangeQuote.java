package com.tesoro.domain.exchange;

import com.tesoro.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Price of one unit of {@code base} expressed in {@code quote} currency (e.g. 1 USD = 1450 ARS).
 * {@code bid} is the price at which the platform buys the base currency and {@code ask} the price at
 * which it sells it, so {@code bid <= ask} and the difference is the spread.
 */
public record ExchangeQuote(
        CurrencyCode base, CurrencyCode quote, BigDecimal bid, BigDecimal ask, String source, Instant asOf) {

    public ExchangeQuote {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(bid, "bid");
        Objects.requireNonNull(ask, "ask");
        Objects.requireNonNull(asOf, "asOf");
        if (base == quote) {
            throw new IllegalArgumentException("A quote needs two different currencies");
        }
        if (bid.signum() <= 0 || ask.signum() <= 0) {
            throw new IllegalArgumentException("Exchange rates must be positive");
        }
        if (bid.compareTo(ask) > 0) {
            throw new IllegalArgumentException("bid must not be greater than ask");
        }
    }

    public boolean covers(CurrencyCode from, CurrencyCode to) {
        return (from == base && to == quote) || (from == quote && to == base);
    }
}
