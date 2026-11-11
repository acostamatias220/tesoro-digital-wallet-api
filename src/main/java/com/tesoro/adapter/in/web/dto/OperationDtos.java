package com.tesoro.adapter.in.web.dto;

import com.tesoro.domain.ledger.EntryType;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Direction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OperationDtos {

    /** Upper bound per operation; a real product would apply per-user limits too. */
    public static final String MAX_AMOUNT = "100000000.00";

    private OperationDtos() {}

    public record AmountRequest(
            @NotNull CurrencyCode currency,

            @NotNull
            @Positive
            @Digits(integer = 9, fraction = 2, message = "Máximo 2 decimales")
            @DecimalMax(MAX_AMOUNT)
            @Schema(example = "15000.50")
            BigDecimal amount) {}

    public record TransferRequest(
            @NotBlank @Email @Schema(example = "bruno@example.com")
            String recipientEmail,

            @NotNull CurrencyCode currency,

            @NotNull
            @Positive
            @Digits(integer = 9, fraction = 2, message = "Máximo 2 decimales")
            @DecimalMax(MAX_AMOUNT)
            @Schema(example = "2500.00")
            BigDecimal amount,

            @Size(max = 140) @Schema(example = "Pizza del viernes")
            String description) {}

    public record ConversionRequest(
            @NotNull @Schema(example = "ARS") CurrencyCode fromCurrency,
            @NotNull @Schema(example = "USD") CurrencyCode toCurrency,

            @NotNull
            @Positive
            @Digits(integer = 9, fraction = 2, message = "Máximo 2 decimales")
            @DecimalMax(MAX_AMOUNT)
            @Schema(example = "145000.00")
            BigDecimal amount) {}

    public record MovementResponse(
            UUID transactionId,
            EntryType type,
            String description,
            Instant createdAt,
            List<WalletChangeResponse> walletChanges,
            Map<String, String> details) {}

    public record WalletChangeResponse(
            UUID accountId, CurrencyCode currency, Direction direction, BigDecimal amount, BigDecimal balanceAfter) {}

    public record QuoteResponse(
            CurrencyCode from,
            CurrencyCode to,
            BigDecimal amount,
            BigDecimal convertedAmount,

            @Schema(description = "Unidades de la moneda destino por unidad de la moneda origen")
            BigDecimal rate,

            BigDecimal bid,
            BigDecimal ask,
            String source,
            Instant asOf) {}
}
