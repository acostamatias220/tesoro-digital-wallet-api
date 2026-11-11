package com.tesoro.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Requests per minute allowed for each kind of endpoint ({@code tesoro.rate-limit.*}). */
@Validated
@ConfigurationProperties("tesoro.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @Positive int authPerMinute,
        @Positive int operationsPerMinute,
        @Positive int defaultPerMinute) {}
