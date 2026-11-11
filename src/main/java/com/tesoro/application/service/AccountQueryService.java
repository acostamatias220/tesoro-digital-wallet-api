package com.tesoro.application.service;

import com.tesoro.application.exception.NotFoundException;
import com.tesoro.application.model.AccountMovement;
import com.tesoro.application.model.MovementFilter;
import com.tesoro.application.model.Requester;
import com.tesoro.application.model.TransactionDetails;
import com.tesoro.application.port.in.AccountQueryUseCase;
import com.tesoro.application.port.out.AccountRepository;
import com.tesoro.application.port.out.LedgerRepository;
import com.tesoro.domain.model.Account;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side. Resources the caller does not own are reported as "not found" (404) instead of
 * "forbidden" (403), so the API does not reveal which ids exist.
 */
@Service
@Transactional(readOnly = true)
public class AccountQueryService implements AccountQueryUseCase {

    private final AccountRepository accounts;
    private final LedgerRepository ledger;

    public AccountQueryService(AccountRepository accounts, LedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Override
    public List<Account> myAccounts(UUID userId) {
        return accounts.findByOwner(userId);
    }

    @Override
    public Account account(Requester requester, UUID accountId) {
        return accounts.findById(accountId)
                .filter(account -> requester.admin() || account.isOwnedBy(requester.userId()))
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada"));
    }

    @Override
    public Page<AccountMovement> movements(
            Requester requester, UUID accountId, MovementFilter filter, Pageable pageable) {
        Account account = account(requester, accountId);
        return ledger.findMovements(account.id(), filter, pageable);
    }

    @Override
    public TransactionDetails transaction(Requester requester, UUID transactionId) {
        if (!requester.admin() && !ledger.involvesUser(transactionId, requester.userId())) {
            throw new NotFoundException("Transacción no encontrada");
        }
        return ledger.findDetails(transactionId).orElseThrow(() -> new NotFoundException("Transacción no encontrada"));
    }
}
