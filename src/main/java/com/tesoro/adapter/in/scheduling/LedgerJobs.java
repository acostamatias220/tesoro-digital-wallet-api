package com.tesoro.adapter.in.scheduling;

import com.tesoro.application.port.in.OutboxRelayUseCase;
import com.tesoro.application.port.in.ReconciliationUseCase;
import com.tesoro.application.port.out.IdempotencyStore;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background jobs. The outbox relay is safe to run on several instances ({@code SKIP LOCKED}); the
 * reconciliation is read-only, so running it twice is harmless (a distributed lock such as ShedLock
 * would avoid the duplicate work).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "tesoro.jobs.enabled", havingValue = "true", matchIfMissing = true)
class LedgerJobs {

    private static final Logger log = LoggerFactory.getLogger(LedgerJobs.class);

    private final ReconciliationUseCase reconciliation;
    private final OutboxRelayUseCase outboxRelay;
    private final IdempotencyStore idempotencyStore;
    private final Clock clock;

    LedgerJobs(
            ReconciliationUseCase reconciliation,
            OutboxRelayUseCase outboxRelay,
            IdempotencyStore idempotencyStore,
            Clock clock) {
        this.reconciliation = reconciliation;
        this.outboxRelay = outboxRelay;
        this.idempotencyStore = idempotencyStore;
        this.clock = clock;
    }

    @Scheduled(cron = "${tesoro.jobs.reconciliation-cron}")
    void reconcileLedger() {
        reconciliation.run("SCHEDULED");
    }

    @Scheduled(
            fixedDelayString = "${tesoro.jobs.outbox-delay-ms}",
            initialDelayString = "${tesoro.jobs.outbox-delay-ms}")
    void relayOutbox() {
        int published;
        do {
            published = outboxRelay.publishPending();
        } while (published > 0 && !Thread.currentThread().isInterrupted());
    }

    @Scheduled(cron = "${tesoro.jobs.idempotency-cleanup-cron}")
    @Transactional
    void purgeExpiredIdempotencyKeys() {
        int deleted = idempotencyStore.deleteExpired(clock.instant());
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency keys", deleted);
        }
    }
}
