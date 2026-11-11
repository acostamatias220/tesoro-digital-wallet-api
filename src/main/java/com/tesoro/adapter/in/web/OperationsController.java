package com.tesoro.adapter.in.web;

import com.tesoro.adapter.in.web.dto.OperationDtos.AmountRequest;
import com.tesoro.adapter.in.web.dto.OperationDtos.ConversionRequest;
import com.tesoro.adapter.in.web.dto.OperationDtos.MovementResponse;
import com.tesoro.adapter.in.web.dto.OperationDtos.QuoteResponse;
import com.tesoro.adapter.in.web.dto.OperationDtos.TransferRequest;
import com.tesoro.adapter.in.web.support.CurrentUser;
import com.tesoro.application.command.ConvertCommand;
import com.tesoro.application.command.DepositCommand;
import com.tesoro.application.command.TransferCommand;
import com.tesoro.application.command.WithdrawCommand;
import com.tesoro.application.model.IdempotentResult;
import com.tesoro.application.model.MovementResult;
import com.tesoro.application.port.in.ExchangeRateUseCase;
import com.tesoro.application.port.in.WalletOperationsUseCase;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Money-moving endpoints. All of them require an {@code Idempotency-Key} header: retrying a request
 * with the same key returns the original response (with {@code Idempotent-Replayed: true}) and never
 * moves money twice.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Operaciones", description = "Depósitos, extracciones, transferencias y conversiones")
class OperationsController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";
    private static final String KEY_PATTERN = "^[A-Za-z0-9_-]{8,100}$";
    private static final String KEY_MESSAGE = "Debe tener entre 8 y 100 caracteres (letras, números, - o _)";

    private final WalletOperationsUseCase operations;
    private final ExchangeRateUseCase exchangeRates;
    private final WebMapper mapper;

    OperationsController(WalletOperationsUseCase operations, ExchangeRateUseCase exchangeRates, WebMapper mapper) {
        this.operations = operations;
        this.exchangeRates = exchangeRates;
        this.mapper = mapper;
    }

    @PostMapping("/deposits")
    @Operation(summary = "Acredita un depósito (simula una transferencia bancaria entrante)")
    ResponseEntity<MovementResponse> deposit(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Clave única por operación, p. ej. un UUID")
                    @RequestHeader(IDEMPOTENCY_KEY)
                    @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE)
                    String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        var result = operations.deposit(new DepositCommand(
                CurrentUser.id(jwt), idempotencyKey, Money.of(request.amount(), request.currency())));
        return created(result);
    }

    @PostMapping("/withdrawals")
    @Operation(summary = "Extrae dinero de una billetera hacia la cuenta bancaria")
    ResponseEntity<MovementResponse> withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        var result = operations.withdraw(new WithdrawCommand(
                CurrentUser.id(jwt), idempotencyKey, Money.of(request.amount(), request.currency())));
        return created(result);
    }

    @PostMapping("/transfers")
    @Operation(summary = "Transfiere a otro usuario (P2P) en la misma moneda")
    ResponseEntity<MovementResponse> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        var result = operations.transfer(new TransferCommand(
                CurrentUser.id(jwt),
                idempotencyKey,
                request.recipientEmail(),
                Money.of(request.amount(), request.currency()),
                request.description()));
        return created(result);
    }

    @PostMapping("/conversions")
    @Operation(summary = "Convierte entre mis billeteras de ARS y USD con la cotización actual")
    ResponseEntity<MovementResponse> convert(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String idempotencyKey,
            @Valid @RequestBody ConversionRequest request) {
        var result = operations.convert(new ConvertCommand(
                CurrentUser.id(jwt),
                idempotencyKey,
                Money.of(request.amount(), request.fromCurrency()),
                request.toCurrency()));
        return created(result);
    }

    @GetMapping("/fx/quote")
    @Operation(summary = "Cotiza una conversión sin ejecutarla")
    QuoteResponse quote(
            @RequestParam CurrencyCode from,
            @RequestParam CurrencyCode to,
            @RequestParam @Positive @Digits(integer = 9, fraction = 2) @DecimalMax("100000000.00") BigDecimal amount) {
        return mapper.toResponse(exchangeRates.quote(Money.of(amount, from), to));
    }

    private ResponseEntity<MovementResponse> created(IdempotentResult<MovementResult> result) {
        MovementResponse body = mapper.toResponse(result.value());
        return ResponseEntity.created(URI.create("/api/v1/transactions/" + body.transactionId()))
                .header(REPLAYED, String.valueOf(result.replayed()))
                .body(body);
    }
}
