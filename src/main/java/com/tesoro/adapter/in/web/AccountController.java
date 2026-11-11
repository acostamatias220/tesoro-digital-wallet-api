package com.tesoro.adapter.in.web;

import com.tesoro.adapter.in.web.dto.AccountDtos.AccountResponse;
import com.tesoro.adapter.in.web.dto.AccountDtos.MovementLineResponse;
import com.tesoro.adapter.in.web.dto.AccountDtos.TransactionResponse;
import com.tesoro.adapter.in.web.dto.PageResponse;
import com.tesoro.adapter.in.web.support.CurrentUser;
import com.tesoro.application.model.MovementFilter;
import com.tesoro.application.port.in.AccountQueryUseCase;
import com.tesoro.domain.ledger.EntryType;
import com.tesoro.domain.model.Direction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Cuentas", description = "Billeteras, extractos y detalle de transacciones")
class AccountController {

    private final AccountQueryUseCase accounts;
    private final WebMapper mapper;

    AccountController(AccountQueryUseCase accounts, WebMapper mapper) {
        this.accounts = accounts;
        this.mapper = mapper;
    }

    @GetMapping("/accounts")
    @Operation(summary = "Mis billeteras con su saldo")
    List<AccountResponse> myAccounts(@AuthenticationPrincipal Jwt jwt) {
        return accounts.myAccounts(CurrentUser.id(jwt)).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Detalle de una billetera")
    AccountResponse account(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        return mapper.toResponse(accounts.account(CurrentUser.requester(jwt), accountId));
    }

    @GetMapping("/accounts/{accountId}/movements")
    @Operation(summary = "Extracto paginado con filtros", description = "Ordenado del más reciente al más antiguo.")
    PageResponse<MovementLineResponse> movements(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @RequestParam(required = false) EntryType type,
            @RequestParam(required = false) Direction direction,
            @Parameter(description = "Desde (inclusive), ISO-8601", example = "2026-01-01T00:00:00Z")
                    @RequestParam(required = false)
                    Instant from,
            @Parameter(description = "Hasta (exclusive), ISO-8601") @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = accounts.movements(
                CurrentUser.requester(jwt),
                accountId,
                new MovementFilter(type, direction, from, to),
                PageRequest.of(page, size));
        return PageResponse.of(result, mapper::toResponse);
    }

    @GetMapping("/transactions/{transactionId}")
    @Operation(summary = "Asiento contable completo de una transacción en la que participé")
    TransactionResponse transaction(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId) {
        return mapper.toResponse(accounts.transaction(CurrentUser.requester(jwt), transactionId));
    }
}
