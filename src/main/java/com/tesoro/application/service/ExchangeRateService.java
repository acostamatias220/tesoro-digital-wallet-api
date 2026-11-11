package com.tesoro.application.service;

import com.tesoro.application.model.ConversionQuote;
import com.tesoro.application.port.in.ExchangeRateUseCase;
import com.tesoro.application.port.out.ExchangeRateProvider;
import com.tesoro.domain.exchange.Conversion;
import com.tesoro.domain.exchange.CurrencyConverter;
import com.tesoro.domain.exchange.ExchangeQuote;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Money;
import org.springframework.stereotype.Service;

@Service
public class ExchangeRateService implements ExchangeRateUseCase {

    private final ExchangeRateProvider provider;

    public ExchangeRateService(ExchangeRateProvider provider) {
        this.provider = provider;
    }

    @Override
    public ConversionQuote quote(Money amount, CurrencyCode target) {
        ExchangeQuote quote = provider.currentQuote(CurrencyCode.USD, CurrencyCode.ARS);
        Conversion conversion = CurrencyConverter.convert(amount, target, quote);
        return new ConversionQuote(
                amount.currency(),
                target,
                amount.amount(),
                conversion.target().amount(),
                conversion.rate(),
                quote.bid(),
                quote.ask(),
                quote.source(),
                quote.asOf());
    }
}
