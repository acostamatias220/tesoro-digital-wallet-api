package com.tesoro.application.port.out;

import com.tesoro.application.model.ReconciliationIssue;
import com.tesoro.application.model.ReconciliationReport;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Read-only checks over the whole ledger plus the history of reconciliation runs. */
public interface ReconciliationRepository {

    List<ReconciliationIssue> findUnbalancedEntries();

    List<ReconciliationIssue> findWalletBalanceMismatches();

    List<ReconciliationIssue> findTrialBalanceDifferences();

    List<ReconciliationIssue> findNegativeWallets();

    long countWallets();

    long countEntries();

    void save(ReconciliationReport report);

    Page<ReconciliationReport> findAll(Pageable pageable);
}
