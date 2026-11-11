package com.tesoro.application.model;

import com.tesoro.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;

/** Preview of a conversion with the current rate, without moving money. */
public record ConversionQuote(
        CurrencyCode from,
        CurrencyCode to,
        BigDecimal amount,
        BigDecimal convertedAmount,
        BigDecimal rate,
        BigDecimal bid,
        BigDecimal ask,
        String source,
        Instant asOf) {}
