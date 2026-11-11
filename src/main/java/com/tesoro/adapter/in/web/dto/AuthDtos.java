package com.tesoro.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) @Schema(example = "ana@example.com")
            String email,

            @NotBlank
            @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres")
            @Schema(example = "una-clave-larga-y-unica")
            String password,

            @NotBlank @Size(max = 120) @Schema(example = "Ana Gómez")
            String fullName) {

        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + "]";
        }
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 72) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + "]";
        }
    }

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {

        @Override
        public String toString() {
            return "RefreshRequest[***]";
        }
    }

    public record TokenResponse(
            @Schema(example = "Bearer") String tokenType,
            String accessToken,

            @Schema(description = "Segundos hasta que vence el access token", example = "900")
            long expiresIn,

            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt) {}

    public record MeResponse(String id, String email, String fullName, String role, Instant createdAt) {}
}
