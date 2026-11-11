package com.tesoro.domain.exchange;

import static com.tesoro.domain.model.CurrencyCode.ARS;
import static com.tesoro.domain.model.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tesoro.domain.exception.InvalidAmountException;
import com.tesoro.domain.model.Money;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CurrencyConverterTest {

    /** 1 USD: the platform buys at 1400 ARS and sells at 1450 ARS. */
    private final ExchangeQuote quote = new ExchangeQuote(
            USD, ARS, new BigDecimal("1400"), new BigDecimal("1450"), "test", Instant.parse("2026-05-01T12:00:00Z"));

    @Test
    void sellingUsdUsesTheBid() {
        Conversion conversion = CurrencyConverter.convert(Money.of("10", USD), ARS, quote);
        assertThat(conversion.target()).isEqualTo(Money.of("14000", ARS));
        assertThat(conversion.rate()).isEqualByComparingTo("1400");
    }

    @Test
    void buyingUsdUsesTheAskAndRoundsDown() {
        // 1000 / 1450 = 0.689655... -> 0.68 (never 0.69: the user can't receive more than the exact value)
        Conversion conversion = CurrencyConverter.convert(Money.of("1000", ARS), USD, quote);
        assertThat(conversion.target()).isEqualTo(Money.of("0.68", USD));
        assertThat(conversion.source()).isEqualTo(Money.of("1000", ARS));
        assertThat(conversion.rate()).isEqualByComparingTo("0.0006896552");
    }

    @ParameterizedTest
    @CsvSource({"14500, 10.00", "1450, 1.00", "1449.99, 0.99", "2.90, 0.00"})
    void neverCreatesMoneyThroughRounding(String ars, String expectedUsd) {
        Money source = Money.of(ars, ARS);
        if (new BigDecimal(expectedUsd).signum() == 0) {
            assertThatThrownBy(() -> CurrencyConverter.convert(source, USD, quote))
                    .isInstanceOf(InvalidAmountException.class)
                    .hasMessageContaining("demasiado chico");
            return;
        }
        Money usd = CurrencyConverter.convert(source, USD, quote).target();
        assertThat(usd).isEqualTo(Money.of(expectedUsd, USD));
        // Value check: what the user receives, priced at the ask, never exceeds what they paid.
        assertThat(usd.amount().multiply(quote.ask())).isLessThanOrEqualTo(source.amount());
    }

    @Test
    void roundTripLosesOnlyTheSpread() {
        Money start = Money.of("100", USD);
        Money ars = CurrencyConverter.convert(start, ARS, quote).target();
        Money back = CurrencyConverter.convert(ars, USD, quote).target();
        assertThat(back.isLessThan(start)).isTrue();
        assertThat(back).isEqualTo(Money.of("96.55", USD));
    }

    @Test
    void rejectsInvalidRequests() {
        assertThatThrownBy(() -> CurrencyConverter.convert(Money.of("1", USD), USD, quote))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> CurrencyConverter.convert(Money.zero(USD), ARS, quote))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void quoteValidatesItsInvariants() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new ExchangeQuote(USD, ARS, BigDecimal.TEN, BigDecimal.ONE, "x", now))
                .hasMessageContaining("bid");
        assertThatThrownBy(() -> new ExchangeQuote(USD, USD, BigDecimal.ONE, BigDecimal.ONE, "x", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExchangeQuote(USD, ARS, BigDecimal.ZERO, BigDecimal.ONE, "x", now))
                .hasMessageContaining("positive");
        assertThat(quote.covers(ARS, USD)).isTrue();
    }
}
