package com.tesoro.application.port.in;

import com.tesoro.application.model.ConversionQuote;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Money;

public interface ExchangeRateUseCase {

    ConversionQuote quote(Money amount, CurrencyCode target);
}
