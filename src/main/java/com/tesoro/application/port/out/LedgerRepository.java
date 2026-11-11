package com.tesoro.application.port.out;

import com.tesoro.application.model.AccountMovement;
import com.tesoro.application.model.MovementFilter;
import com.tesoro.application.model.SystemAccountBalance;
import com.tesoro.application.model.TransactionDetails;
import com.tesoro.domain.ledger.JournalEntry;
import com.tesoro.domain.model.Money;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Append-only store of journal entries and their postings. */
public interface LedgerRepository {

    /**
     * Appends a balanced entry. {@code balancesAfter} holds the resulting balance of the wallets
     * involved, stored on each posting to build statements without recomputing running sums.
     */
    void append(JournalEntry entry, Map<UUID, Money> balancesAfter);

    Optional<TransactionDetails> findDetails(UUID entryId);

    /** Whether any posting of the entry belongs to an account owned by the user. */
    boolean involvesUser(UUID entryId, UUID userId);

    Page<AccountMovement> findMovements(UUID accountId, MovementFilter filter, Pageable pageable);

    List<SystemAccountBalance> systemBalances();
}
