package com.tesoro.application.port.out;

import com.tesoro.domain.exchange.ExchangeQuote;
import com.tesoro.domain.model.CurrencyCode;

public interface ExchangeRateProvider {

    /**
     * Current price of one {@code base} in {@code quote}.
     *
     * @throws com.tesoro.application.exception.ExchangeRateUnavailableException if no reliable rate
     *     can be obtained
     */
    ExchangeQuote currentQuote(CurrencyCode base, CurrencyCode quote);
}
