package com.tesoro.domain.model;

import static com.tesoro.domain.model.CurrencyCode.ARS;
import static com.tesoro.domain.model.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tesoro.domain.exception.CurrencyMismatchException;
import com.tesoro.domain.exception.InvalidAmountException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void normalizesToCurrencyScaleSoEqualityIsByValue() {
        assertThat(Money.of("10", ARS)).isEqualTo(Money.of("10.00", ARS));
        assertThat(Money.of("10.5000", ARS).amount()).isEqualByComparingTo("10.50");
        assertThat(Money.of("10", ARS).amount().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "1.999", "100.105"})
    void rejectsMoreDecimalsThanTheCurrencyAllowsInsteadOfRounding(String amount) {
        assertThatThrownBy(() -> Money.of(amount, USD))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("2 decimales");
    }

    @Test
    void addsAndSubtractsWithoutFloatingPointErrors() {
        Money result = Money.of("0.10", USD).plus(Money.of("0.20", USD));
        assertThat(result).isEqualTo(Money.of("0.30", USD));
        assertThat(result.minus(Money.of("0.30", USD)).isZero()).isTrue();
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of("1", ARS).plus(Money.of("1", USD)))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> Money.of("1", ARS).compareTo(Money.of("1", USD)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void comparesAndReportsSign() {
        Money ten = Money.of("10", ARS);
        assertThat(ten.isPositive()).isTrue();
        assertThat(ten.negate().isNegative()).isTrue();
        assertThat(Money.zero(ARS).isZero()).isTrue();
        assertThat(Money.of("9.99", ARS).isLessThan(ten)).isTrue();
        assertThat(ten.toString()).isEqualTo("10.00 ARS");
    }

    @Test
    void roundsOnlyWhenAskedExplicitly() {
        BigDecimal exact = new BigDecimal("12.3456");
        assertThat(Money.rounded(exact, USD, RoundingMode.DOWN).amount()).isEqualByComparingTo("12.34");
        assertThat(Money.rounded(exact, USD, RoundingMode.HALF_EVEN).amount()).isEqualByComparingTo("12.35");
    }
}
