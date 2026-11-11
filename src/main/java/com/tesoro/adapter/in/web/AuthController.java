package com.tesoro.adapter.in.web;

import com.tesoro.adapter.in.web.dto.AuthDtos.LoginRequest;
import com.tesoro.adapter.in.web.dto.AuthDtos.MeResponse;
import com.tesoro.adapter.in.web.dto.AuthDtos.RefreshRequest;
import com.tesoro.adapter.in.web.dto.AuthDtos.RegisterRequest;
import com.tesoro.adapter.in.web.dto.AuthDtos.TokenResponse;
import com.tesoro.adapter.in.web.support.CurrentUser;
import com.tesoro.application.command.RegisterCommand;
import com.tesoro.application.model.AuthTokens;
import com.tesoro.application.port.in.AuthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Auth", description = "Registro, login y rotación de tokens")
class AuthController {

    private final AuthUseCase auth;
    private final WebMapper mapper;
    private final Clock clock;

    AuthController(AuthUseCase auth, WebMapper mapper, Clock clock) {
        this.auth = auth;
        this.mapper = mapper;
        this.clock = clock;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Crea un usuario con billeteras en ARS y USD y devuelve sus tokens")
    TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return toResponse(auth.register(new RegisterCommand(request.email(), request.password(), request.fullName())));
    }

    @PostMapping("/auth/login")
    @SecurityRequirements
    @Operation(summary = "Login con email y contraseña")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(auth.login(request.email(), request.password()));
    }

    @PostMapping("/auth/refresh")
    @SecurityRequirements
    @Operation(
            summary = "Rota el refresh token",
            description = "Cada refresh token sirve una sola vez. Reutilizar uno ya usado cierra toda la sesión.")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toResponse(auth.refresh(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirements
    @Operation(summary = "Revoca la sesión (toda la familia del refresh token)")
    void logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "Datos del usuario autenticado")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return mapper.toMe(auth.currentUser(CurrentUser.id(jwt)));
    }

    private TokenResponse toResponse(AuthTokens tokens) {
        long expiresIn = Math.max(
                0,
                Duration.between(clock.instant(), tokens.accessTokenExpiresAt()).toSeconds());
        return new TokenResponse(
                "Bearer",
                tokens.accessToken(),
                expiresIn,
                tokens.accessTokenExpiresAt(),
                tokens.refreshToken(),
                tokens.refreshTokenExpiresAt());
    }
}
