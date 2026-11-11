package com.tesoro.application.service;

import com.tesoro.application.model.ReconciliationIssue;
import com.tesoro.application.model.ReconciliationReport;
import com.tesoro.application.port.in.ReconciliationUseCase;
import com.tesoro.application.port.out.ReconciliationRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the ledger invariants: every entry balances per currency, global debits equal credits,
 * no wallet is negative and every cached wallet balance equals the sum of its postings.
 */
@Service
public class ReconciliationService implements ReconciliationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationRepository repository;
    private final Clock clock;
    private final AtomicInteger lastIssueCount = new AtomicInteger();

    public ReconciliationService(ReconciliationRepository repository, Clock clock, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.clock = clock;
        Gauge.builder("tesoro.reconciliation.issues", lastIssueCount, AtomicInteger::get)
                .description("Ledger invariant violations found by the last reconciliation run")
                .register(meterRegistry);
    }

    /**
     * REPEATABLE READ gives every check the same snapshot, so movements committed while the run is in
     * progress cannot produce false positives.
     */
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport run(String trigger) {
        Instant startedAt = clock.instant();
        List<ReconciliationIssue> issues = new ArrayList<>();
        issues.addAll(repository.findUnbalancedEntries());
        issues.addAll(repository.findTrialBalanceDifferences());
        issues.addAll(repository.findNegativeWallets());
        issues.addAll(repository.findWalletBalanceMismatches());

        var status = issues.isEmpty() ? ReconciliationReport.Status.OK : ReconciliationReport.Status.MISMATCH;
        var report = new ReconciliationReport(
                UUID.randomUUID(),
                trigger,
                status,
                repository.countWallets(),
                repository.countEntries(),
                List.copyOf(issues),
                startedAt,
                clock.instant());
        repository.save(report);
        lastIssueCount.set(issues.size());

        if (status == ReconciliationReport.Status.OK) {
            log.info(
                    "Ledger reconciliation OK: {} wallets, {} entries",
                    report.walletsChecked(),
                    report.entriesChecked());
        } else {
            log.error("Ledger reconciliation found {} issue(s): {}", issues.size(), issues);
        }
        return report;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReconciliationReport> history(Pageable pageable) {
        return repository.findAll(pageable);
    }
}
