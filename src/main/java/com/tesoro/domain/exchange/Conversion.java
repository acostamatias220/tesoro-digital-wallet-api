package com.tesoro.domain.exchange;

import com.tesoro.domain.model.Money;
import java.math.BigDecimal;

/** Result of converting {@code source} into {@code target} using {@code rate} (target per source). */
public record Conversion(Money source, Money target, BigDecimal rate, ExchangeQuote quote) {}
