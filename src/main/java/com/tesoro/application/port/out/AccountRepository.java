package com.tesoro.application.port.out;

import com.tesoro.domain.model.Account;
import com.tesoro.domain.model.AccountKind;
import com.tesoro.domain.model.CurrencyCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Optional<Account> findById(UUID id);

    List<Account> findByOwner(UUID ownerId);

    Optional<Account> findWallet(UUID ownerId, CurrencyCode currency);

    Account getSystemAccount(AccountKind kind, CurrencyCode currency);

    /**
     * Locks the row ({@code SELECT ... FOR UPDATE}) until the end of the current transaction and
     * returns its latest committed state. Callers must lock several accounts in a stable order.
     */
    Account lockForUpdate(UUID id);

    void insert(Account account);

    /** Persists the cached balance and status of an account loaded in this transaction. */
    void update(Account account);
}
