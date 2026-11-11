package com.tesoro.application.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Opaque refresh token. Only its SHA-256 hash is stored. Tokens issued from one login share a
 * {@code familyId}, so presenting an already rotated token revokes the whole family.
 */
public record RefreshToken(
        UUID id,
        UUID userId,
        UUID familyId,
        String tokenHash,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
