package com.tesoro.application.model;

/** A ledger invariant that does not hold. */
public record ReconciliationIssue(Type type, String reference, String expected, String actual) {

    public enum Type {
        /** A journal entry whose debits and credits differ for some currency. */
        UNBALANCED_ENTRY,
        /** A wallet whose cached balance differs from the sum of its postings. */
        BALANCE_MISMATCH,
        /** Global debits and credits for a currency differ (trial balance). */
        TRIAL_BALANCE,
        /** A wallet with a negative balance. */
        NEGATIVE_BALANCE
    }
}
