package com.tesoro.application.service;

import com.tesoro.application.model.OutboxMessage;
import com.tesoro.application.port.in.OutboxRelayUseCase;
import com.tesoro.application.port.out.EventPublisher;
import com.tesoro.application.port.out.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays outbox messages. Delivery is <em>at least once</em>: if publishing succeeds but the commit
 * fails, the message is sent again, so consumers deduplicate by message id.
 */
@Service
public class OutboxRelayService implements OutboxRelayUseCase {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxRepository outbox;
    private final EventPublisher publisher;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final Counter published;
    private final Counter failed;

    public OutboxRelayService(
            OutboxRepository outbox,
            EventPublisher publisher,
            Clock clock,
            MeterRegistry meterRegistry,
            @Value("${tesoro.outbox.batch-size:50}") int batchSize,
            @Value("${tesoro.outbox.max-attempts:10}") int maxAttempts) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.published = meterRegistry.counter("tesoro.outbox.published");
        this.failed = meterRegistry.counter("tesoro.outbox.failed");
        meterRegistry.gauge("tesoro.outbox.pending", outbox, OutboxRepository::countPending);
    }

    @Override
    @Transactional
    public int publishPending() {
        List<OutboxMessage> batch = outbox.lockNextBatch(batchSize, maxAttempts);
        int count = 0;
        for (OutboxMessage message : batch) {
            try {
                publisher.publish(message);
                outbox.markPublished(message.id(), clock.instant());
                published.increment();
                count++;
            } catch (RuntimeException e) {
                log.warn("Could not publish outbox message {}: {}", message.id(), e.getMessage());
                outbox.markFailed(message.id(), e.getClass().getSimpleName() + ": " + e.getMessage());
                failed.increment();
            }
        }
        return count;
    }
}
