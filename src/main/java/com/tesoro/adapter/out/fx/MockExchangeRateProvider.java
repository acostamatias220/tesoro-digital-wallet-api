package com.tesoro.adapter.out.fx;

import com.tesoro.application.port.out.ExchangeRateProvider;
import com.tesoro.config.FxProperties;
import com.tesoro.domain.exchange.ExchangeQuote;
import com.tesoro.domain.model.CurrencyCode;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Deterministic USD/ARS quote taken from configuration. */
@Component
@ConditionalOnProperty(name = "tesoro.fx.provider", havingValue = "mock", matchIfMissing = true)
class MockExchangeRateProvider implements ExchangeRateProvider {

    private final FxProperties.Mock settings;
    private final Clock clock;

    MockExchangeRateProvider(FxProperties properties, Clock clock) {
        this.settings = properties.mock();
        this.clock = clock;
    }

    @Override
    public ExchangeQuote currentQuote(CurrencyCode base, CurrencyCode quote) {
        UsdArs.require(base, quote);
        return new ExchangeQuote(base, quote, settings.bid(), settings.ask(), "mock", clock.instant());
    }
}
