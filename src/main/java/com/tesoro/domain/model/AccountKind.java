package com.tesoro.domain.model;

/**
 * Kind of ledger account. The normal side decides whether a debit or a credit increases the
 * balance, following standard double-entry conventions from the platform's point of view.
 */
public enum AccountKind {
    /** Money the platform owes its users (a liability): credits increase it. */
    USER_WALLET(Direction.CREDIT),
    /** Funds held at the partner bank (an asset): debits increase it. */
    SETTLEMENT(Direction.DEBIT),
    /** Clearing account used to swap currencies. Its balance is the platform's FX position. */
    FX_POOL(Direction.CREDIT);

    private final Direction normalSide;

    AccountKind(Direction normalSide) {
        this.normalSide = normalSide;
    }

    public Direction normalSide() {
        return normalSide;
    }

    /** Only user wallets keep a cached balance that is locked and validated on every movement. */
    public boolean tracksBalance() {
        return this == USER_WALLET;
    }
}
