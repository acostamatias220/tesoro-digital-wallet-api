package com.tesoro.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency keys scoped per user. Both methods must run inside the same transaction as the
 * operation they protect, so the key and the money movement are committed (or rolled back)
 * together.
 */
public interface IdempotencyStore {

    /**
     * Tries to claim the key. Returns empty when the caller owns the key and must run the operation,
     * or the stored response when an earlier request with the same key already completed. A
     * concurrent request with the same key blocks until the first one commits or rolls back.
     *
     * @throws com.tesoro.application.exception.IdempotencyKeyReusedException if the key was used
     *     with a different request body
     */
    <T> Optional<T> claim(UUID userId, String key, String requestFingerprint, Class<T> responseType);

    void complete(UUID userId, String key, Object response);

    int deleteExpired(Instant now);
}
