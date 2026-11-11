package com.tesoro.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesoro.adapter.out.persistence.entity.ReconciliationRunEntity;
import com.tesoro.adapter.out.persistence.repository.ReconciliationRunJpaRepository;
import com.tesoro.application.model.ReconciliationIssue;
import com.tesoro.application.model.ReconciliationIssue.Type;
import com.tesoro.application.model.ReconciliationReport;
import com.tesoro.application.port.out.ReconciliationRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ledger invariant checks written as set-based SQL, so they scale with the size of the ledger. */
@Component
class JdbcReconciliationAdapter implements ReconciliationRepository {

    private static final String SIGNED_AMOUNT = "CASE WHEN p.direction = 'DEBIT' THEN p.amount ELSE -p.amount END";
    private static final TypeReference<List<ReconciliationIssue>> ISSUES = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ReconciliationRunJpaRepository runs;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    JdbcReconciliationAdapter(
            JdbcTemplate jdbc,
            ReconciliationRunJpaRepository runs,
            ObjectMapper objectMapper,
            EntityManager entityManager) {
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        this.runs = runs;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ReconciliationIssue> findUnbalancedEntries() {
        return jdbc.query(
                "SELECT p.journal_entry_id, p.currency, SUM(" + SIGNED_AMOUNT + ") AS difference"
                        + " FROM postings p GROUP BY p.journal_entry_id, p.currency"
                        + " HAVING SUM(" + SIGNED_AMOUNT + ") <> 0",
                (rs, i) -> new ReconciliationIssue(
                        Type.UNBALANCED_ENTRY,
                        rs.getString("journal_entry_id") + " (" + rs.getString("currency") + ")",
                        "0",
                        rs.getBigDecimal("difference").toPlainString()));
    }

    @Override
    public List<ReconciliationIssue> findWalletBalanceMismatches() {
        // A wallet is a liability: its balance is credits minus debits.
        return jdbc.query(
                "SELECT a.id, a.balance, COALESCE(SUM(-(" + SIGNED_AMOUNT + ")), 0) AS derived"
                        + " FROM accounts a LEFT JOIN postings p ON p.account_id = a.id"
                        + " WHERE a.kind = 'USER_WALLET'"
                        + " GROUP BY a.id, a.balance"
                        + " HAVING a.balance <> COALESCE(SUM(-(" + SIGNED_AMOUNT + ")), 0)",
                (rs, i) -> new ReconciliationIssue(
                        Type.BALANCE_MISMATCH,
                        rs.getString("id"),
                        rs.getBigDecimal("derived").toPlainString(),
                        rs.getBigDecimal("balance").toPlainString()));
    }

    @Override
    public List<ReconciliationIssue> findTrialBalanceDifferences() {
        return jdbc.query(
                "SELECT p.currency,"
                        + " SUM(CASE WHEN p.direction = 'DEBIT' THEN p.amount ELSE 0 END) AS debits,"
                        + " SUM(CASE WHEN p.direction = 'CREDIT' THEN p.amount ELSE 0 END) AS credits"
                        + " FROM postings p GROUP BY p.currency"
                        + " HAVING SUM(" + SIGNED_AMOUNT + ") <> 0",
                (rs, i) -> new ReconciliationIssue(
                        Type.TRIAL_BALANCE,
                        rs.getString("currency"),
                        rs.getBigDecimal("debits").toPlainString(),
                        rs.getBigDecimal("credits").toPlainString()));
    }

    @Override
    public List<ReconciliationIssue> findNegativeWallets() {
        return jdbc.query(
                "SELECT id, balance FROM accounts WHERE kind = 'USER_WALLET' AND balance < 0",
                (rs, i) -> new ReconciliationIssue(
                        Type.NEGATIVE_BALANCE,
                        rs.getString("id"),
                        ">= 0",
                        rs.getBigDecimal("balance").toPlainString()));
    }

    @Override
    public long countWallets() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM accounts WHERE kind = 'USER_WALLET'", Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public long countEntries() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM journal_entries", Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public void save(ReconciliationReport report) {
        ReconciliationRunEntity entity = new ReconciliationRunEntity();
        entity.setId(report.id());
        entity.setTrigger(report.trigger());
        entity.setStatus(report.status().name());
        entity.setWalletsChecked(report.walletsChecked());
        entity.setEntriesChecked(report.entriesChecked());
        entity.setIssues(write(report.issues()));
        entity.setStartedAt(report.startedAt());
        entity.setFinishedAt(report.finishedAt());
        entityManager.persist(entity);
    }

    @Override
    public Page<ReconciliationReport> findAll(Pageable pageable) {
        return runs.findAll(pageable)
                .map(run -> new ReconciliationReport(
                        run.getId(),
                        run.getTrigger(),
                        ReconciliationReport.Status.valueOf(run.getStatus()),
                        run.getWalletsChecked(),
                        run.getEntriesChecked(),
                        read(run.getIssues()),
                        run.getStartedAt(),
                        run.getFinishedAt()));
    }

    private String write(List<ReconciliationIssue> issues) {
        try {
            return objectMapper.writeValueAsString(issues);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<ReconciliationIssue> read(String json) {
        try {
            return objectMapper.readValue(json, ISSUES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
