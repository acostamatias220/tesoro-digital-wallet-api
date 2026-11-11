package com.tesoro.application.model;

import java.time.Instant;

public record AuthTokens(
        String accessToken, Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {}
