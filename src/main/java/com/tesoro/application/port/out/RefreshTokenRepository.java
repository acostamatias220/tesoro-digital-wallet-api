package com.tesoro.application.port.out;

import com.tesoro.application.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void insert(RefreshToken token);

    Optional<RefreshToken> findByHash(String tokenHash);

    /** Revokes one token if it is still active. Returns false if it was already revoked. */
    boolean revoke(UUID tokenId, Instant now);

    int revokeFamily(UUID familyId, Instant now);
}
