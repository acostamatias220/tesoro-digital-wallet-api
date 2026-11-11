package com.tesoro.domain.model;

import com.tesoro.domain.exception.AccountFrozenException;
import com.tesoro.domain.exception.CurrencyMismatchException;
import com.tesoro.domain.exception.InsufficientFundsException;
import com.tesoro.domain.exception.InvalidAmountException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A ledger account. User wallets keep a cached balance that must never go negative; system
 * accounts (settlement, FX pool) derive their balance from postings only, so they are never a lock
 * hotspot.
 */
public class Account {

    private final UUID id;
    private final UUID ownerId;
    private final String code;
    private final CurrencyCode currency;
    private final AccountKind kind;
    private AccountStatus status;
    private Money balance;
    private final Instant createdAt;

    public Account(
            UUID id,
            UUID ownerId,
            String code,
            CurrencyCode currency,
            AccountKind kind,
            AccountStatus status,
            Money balance,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = ownerId;
        this.code = code;
        this.currency = Objects.requireNonNull(currency, "currency");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.status = Objects.requireNonNull(status, "status");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.createdAt = createdAt;
        if (balance.currency() != currency) {
            throw new CurrencyMismatchException(currency, balance.currency());
        }
        if (kind == AccountKind.USER_WALLET && ownerId == null) {
            throw new IllegalArgumentException("A user wallet needs an owner");
        }
    }

    /** Creates an empty, active wallet for a user. */
    public static Account openWallet(UUID ownerId, CurrencyCode currency, Instant now) {
        return new Account(
                UUID.randomUUID(),
                ownerId,
                null,
                currency,
                AccountKind.USER_WALLET,
                AccountStatus.ACTIVE,
                Money.zero(currency),
                now);
    }

    /** Applies a debit. For a wallet this reduces the balance and fails if funds are missing. */
    public void debit(Money amount) {
        apply(Direction.DEBIT, amount);
    }

    /** Applies a credit. For a wallet this increases the balance. */
    public void credit(Money amount) {
        apply(Direction.CREDIT, amount);
    }

    private void apply(Direction direction, Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidAmountException("El monto debe ser mayor a cero");
        }
        if (amount.currency() != currency) {
            throw new CurrencyMismatchException(currency, amount.currency());
        }
        ensureActive();
        if (!kind.tracksBalance()) {
            return;
        }
        Money next = direction == kind.normalSide() ? balance.plus(amount) : balance.minus(amount);
        if (next.isNegative()) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        balance = next;
    }

    public void ensureActive() {
        if (status == AccountStatus.FROZEN) {
            throw new AccountFrozenException(id);
        }
    }

    public void freeze() {
        status = AccountStatus.FROZEN;
    }

    public void activate() {
        status = AccountStatus.ACTIVE;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String code() {
        return code;
    }

    public CurrencyCode currency() {
        return currency;
    }

    public AccountKind kind() {
        return kind;
    }

    public AccountStatus status() {
        return status;
    }

    public Money balance() {
        return balance;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
