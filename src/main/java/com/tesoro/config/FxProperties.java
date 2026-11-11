package com.tesoro.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Exchange-rate provider settings ({@code tesoro.fx.*}). */
@Validated
@ConfigurationProperties("tesoro.fx")
public record FxProperties(
        @NotBlank String provider,
        @NotNull Mock mock,
        @NotNull DolarApi dolarapi) {

    /** Fixed USD/ARS quote used by the mock provider (default for local development and tests). */
    public record Mock(@Positive BigDecimal bid, @Positive BigDecimal ask) {}

    /** Public API https://dolarapi.com (official USD/ARS rate). */
    public record DolarApi(
            @NotBlank String baseUrl,
            @NotBlank String path,
            @NotNull Duration cacheTtl,
            @NotNull Duration maxStaleness,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {}
}
