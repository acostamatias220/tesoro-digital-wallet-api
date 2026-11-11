package com.tesoro.adapter.in.web;

import com.tesoro.adapter.in.web.dto.AccountDtos.AccountResponse;
import com.tesoro.adapter.in.web.dto.AdminDtos.AccountStatusRequest;
import com.tesoro.adapter.in.web.dto.AdminDtos.AuditEntryResponse;
import com.tesoro.adapter.in.web.dto.AdminDtos.OutboxMessageResponse;
import com.tesoro.adapter.in.web.dto.AdminDtos.ReconciliationResponse;
import com.tesoro.adapter.in.web.dto.AdminDtos.SystemAccountResponse;
import com.tesoro.adapter.in.web.dto.AdminDtos.UserResponse;
import com.tesoro.adapter.in.web.dto.PageResponse;
import com.tesoro.adapter.in.web.support.CurrentUser;
import com.tesoro.application.port.in.AdminUseCase;
import com.tesoro.application.port.in.ReconciliationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administración", description = "Solo rol ADMIN: usuarios, congelamiento, ledger, auditoría y outbox")
class AdminController {

    private final AdminUseCase admin;
    private final ReconciliationUseCase reconciliation;
    private final WebMapper mapper;

    AdminController(AdminUseCase admin, ReconciliationUseCase reconciliation, WebMapper mapper) {
        this.admin = admin;
        this.reconciliation = reconciliation;
        this.mapper = mapper;
    }

    @GetMapping("/users")
    @Operation(summary = "Usuarios registrados")
    PageResponse<UserResponse> users(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(admin.users(pageable), mapper::toResponse);
    }

    @GetMapping("/users/{userId}/accounts")
    @Operation(summary = "Billeteras de un usuario")
    List<AccountResponse> accountsOf(@PathVariable UUID userId) {
        return admin.accountsOf(userId).stream().map(mapper::toResponse).toList();
    }

    @PatchMapping("/accounts/{accountId}/status")
    @Operation(summary = "Congela o reactiva una billetera (queda auditado)")
    AccountResponse changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountStatusRequest request) {
        return mapper.toResponse(
                admin.changeAccountStatus(CurrentUser.id(jwt), accountId, request.status(), request.reason()));
    }

    @GetMapping("/ledger/system-accounts")
    @Operation(summary = "Saldos de las cuentas del sistema, derivados de los asientos")
    List<SystemAccountResponse> systemAccounts() {
        return admin.systemBalances().stream().map(mapper::toResponse).toList();
    }

    @PostMapping("/reconciliations")
    @Operation(summary = "Ejecuta ahora la conciliación del ledger")
    ReconciliationResponse reconcile() {
        return mapper.toResponse(reconciliation.run("MANUAL"));
    }

    @GetMapping("/reconciliations")
    @Operation(summary = "Historial de conciliaciones")
    PageResponse<ReconciliationResponse> reconciliations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        return PageResponse.of(reconciliation.history(pageable), mapper::toResponse);
    }

    @GetMapping("/audit-log")
    @Operation(summary = "Registro de auditoría", description = "Filtrable por acción, p. ej. LOGIN_FAILED")
    PageResponse<AuditEntryResponse> auditLog(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        return PageResponse.of(admin.auditLog(action, pageable), mapper::toResponse);
    }

    @GetMapping("/outbox")
    @Operation(summary = "Eventos del outbox (publicados y pendientes)")
    PageResponse<OutboxMessageResponse> outbox(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(admin.outbox(pageable), mapper::toResponse);
    }
}
