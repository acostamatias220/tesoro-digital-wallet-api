package com.tesoro.domain.ledger;

import com.tesoro.domain.model.Direction;
import com.tesoro.domain.model.Money;
import java.util.Objects;
import java.util.UUID;

/** One line of a journal entry: a debit or a credit of a positive amount on one account. */
public record Posting(UUID accountId, Direction direction, Money amount) {

    public Posting {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
    }

    /** Debits count as positive and credits as negative, so a balanced entry sums to zero. */
    public Money signedAmount() {
        return direction == Direction.DEBIT ? amount : amount.negate();
    }
}
