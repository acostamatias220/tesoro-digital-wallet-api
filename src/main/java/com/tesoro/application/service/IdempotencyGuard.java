package com.tesoro.application.service;

import com.tesoro.application.model.IdempotentResult;
import com.tesoro.application.port.out.IdempotencyStore;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs an operation at most once per (user, key). The key is claimed with an {@code INSERT ... ON
 * CONFLICT DO NOTHING} as the first statement of the caller's transaction and completed with the
 * response before commit, so key and side effects become visible atomically.
 */
@Component
public class IdempotencyGuard {

    private final IdempotencyStore store;

    public IdempotencyGuard(IdempotencyStore store) {
        this.store = store;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> IdempotentResult<T> execute(
            UUID userId, String key, String fingerprint, Class<T> type, Supplier<T> operation) {
        Optional<T> previous = store.claim(userId, key, fingerprint, type);
        if (previous.isPresent()) {
            return IdempotentResult.replayed(previous.get());
        }
        T result = operation.get();
        store.complete(userId, key, result);
        return IdempotentResult.fresh(result);
    }
}
