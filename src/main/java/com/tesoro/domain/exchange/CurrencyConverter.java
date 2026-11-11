package com.tesoro.domain.exchange;

import com.tesoro.domain.exception.InvalidAmountException;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Converts amounts between currencies applying the spread in the platform's favour and rounding
 * the result <strong>down</strong> to the target currency scale. Rounding down guarantees that a
 * conversion never creates money out of thin air: the user receives at most the exact value.
 */
public final class CurrencyConverter {

    /** Precision used for intermediate results before the final, explicit rounding. */
    private static final MathContext PRECISION = MathContext.DECIMAL128;

    public static final RoundingMode ROUNDING = RoundingMode.DOWN;

    private CurrencyConverter() {}

    public static Conversion convert(Money source, CurrencyCode target, ExchangeQuote quote) {
        if (!source.isPositive()) {
            throw new InvalidAmountException("El monto a convertir debe ser mayor a cero");
        }
        if (source.currency() == target) {
            throw new InvalidAmountException("La moneda de origen y destino deben ser distintas");
        }
        if (!quote.covers(source.currency(), target)) {
            throw new IllegalArgumentException("Quote %s/%s cannot convert %s to %s"
                    .formatted(quote.base(), quote.quote(), source.currency(), target));
        }

        BigDecimal rate;
        BigDecimal exact;
        if (source.currency() == quote.base()) {
            // User sells the base currency (e.g. USD -> ARS): the platform buys at the bid.
            rate = quote.bid();
            exact = source.amount().multiply(rate, PRECISION);
        } else {
            // User buys the base currency (e.g. ARS -> USD): the platform sells at the ask.
            rate = BigDecimal.ONE.divide(quote.ask(), PRECISION);
            exact = source.amount().divide(quote.ask(), PRECISION);
        }

        Money converted = Money.rounded(exact, target, ROUNDING);
        if (!converted.isPositive()) {
            throw new InvalidAmountException("El monto es demasiado chico para convertirlo a %s".formatted(target));
        }
        return new Conversion(source, converted, rate.stripTrailingZeros(), quote);
    }
}
