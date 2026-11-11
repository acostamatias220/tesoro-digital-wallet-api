package com.tesoro.application.port.out;

import com.tesoro.application.model.OutboxMessage;
import com.tesoro.domain.event.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OutboxRepository {

    /** Stores the event in the current transaction (transactional outbox pattern). */
    void enqueue(DomainEvent event);

    /**
     * Locks up to {@code limit} pending messages with {@code FOR UPDATE SKIP LOCKED}, so several
     * instances can publish in parallel without picking the same message.
     */
    List<OutboxMessage> lockNextBatch(int limit, int maxAttempts);

    void markPublished(UUID id, Instant now);

    void markFailed(UUID id, String error);

    long countPending();

    Page<OutboxMessage> findRecent(Pageable pageable);
}
