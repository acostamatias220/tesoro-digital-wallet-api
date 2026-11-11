package com.tesoro.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings. The HMAC secret comes from the {@code JWT_SECRET} environment variable and must be
 * at least 32 bytes (256 bits) for HS256; the application refuses to start otherwise.
 */
@Validated
@ConfigurationProperties("tesoro.security.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET is required")
        @Size(min = 32, message = "JWT_SECRET must have at least 32 characters")
        String secret,

        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl) {}
