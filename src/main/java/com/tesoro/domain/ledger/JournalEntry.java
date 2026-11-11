package com.tesoro.domain.ledger;

import com.tesoro.domain.exception.CurrencyMismatchException;
import com.tesoro.domain.exception.InvalidAmountException;
import com.tesoro.domain.exception.UnbalancedEntryException;
import com.tesoro.domain.model.Account;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Direction;
import com.tesoro.domain.model.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, balanced set of postings that records one business operation. For every currency
 * involved, the sum of debits equals the sum of credits; an entry that breaks this rule cannot be
 * built. A currency conversion therefore has two balanced "legs", one per currency.
 */
public final class JournalEntry {

    private final UUID id;
    private final EntryType type;
    private final String description;
    private final UUID initiatedBy;
    private final Map<String, String> metadata;
    private final List<Posting> postings;
    private final Instant createdAt;

    private JournalEntry(Builder builder, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.type = builder.type;
        this.description = builder.description;
        this.initiatedBy = builder.initiatedBy;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.postings = List.copyOf(builder.postings);
        this.createdAt = createdAt;
    }

    /** Rehydrates an entry that was already validated and persisted. */
    public JournalEntry(
            UUID id,
            EntryType type,
            String description,
            UUID initiatedBy,
            Map<String, String> metadata,
            List<Posting> postings,
            Instant createdAt) {
        this.id = id;
        this.type = type;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.postings = List.copyOf(postings);
        this.createdAt = createdAt;
        verifyBalanced(this.postings);
    }

    public static Builder builder(EntryType type, String description) {
        return new Builder(type, description);
    }

    static void verifyBalanced(List<Posting> postings) {
        if (postings.size() < 2) {
            throw new UnbalancedEntryException("Un asiento necesita al menos dos movimientos");
        }
        Map<CurrencyCode, Money> totals = new EnumMap<>(CurrencyCode.class);
        for (Posting posting : postings) {
            totals.merge(posting.amount().currency(), posting.signedAmount(), Money::plus);
        }
        totals.forEach((currency, total) -> {
            if (!total.isZero()) {
                throw new UnbalancedEntryException(
                        "El asiento no balancea en %s: diferencia de %s".formatted(currency, total));
            }
        });
    }

    /** Sum of debits for one currency (equal to the sum of credits, since the entry balances). */
    public Money totalDebits(CurrencyCode currency) {
        return postings.stream()
                .filter(p -> p.direction() == Direction.DEBIT && p.amount().currency() == currency)
                .map(Posting::amount)
                .reduce(Money.zero(currency), Money::plus);
    }

    public UUID id() {
        return id;
    }

    public EntryType type() {
        return type;
    }

    public String description() {
        return description;
    }

    public UUID initiatedBy() {
        return initiatedBy;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public List<Posting> postings() {
        return postings;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public static final class Builder {
        private final EntryType type;
        private final String description;
        private final List<Posting> postings = new ArrayList<>();
        private final Map<String, String> metadata = new LinkedHashMap<>();
        private UUID initiatedBy;

        private Builder(EntryType type, String description) {
            this.type = Objects.requireNonNull(type, "type");
            this.description = Objects.requireNonNull(description, "description");
        }

        public Builder initiatedBy(UUID userId) {
            this.initiatedBy = userId;
            return this;
        }

        public Builder metadata(String key, String value) {
            metadata.put(key, value);
            return this;
        }

        public Builder debit(Account account, Money amount) {
            return add(account, Direction.DEBIT, amount);
        }

        public Builder credit(Account account, Money amount) {
            return add(account, Direction.CREDIT, amount);
        }

        private Builder add(Account account, Direction direction, Money amount) {
            if (!amount.isPositive()) {
                throw new InvalidAmountException("Cada movimiento del asiento debe ser positivo");
            }
            if (account.currency() != amount.currency()) {
                throw new CurrencyMismatchException(account.currency(), amount.currency());
            }
            postings.add(new Posting(account.id(), direction, amount));
            return this;
        }

        /** Validates that every currency balances and returns the immutable entry. */
        public JournalEntry build(Instant createdAt) {
            verifyBalanced(postings);
            return new JournalEntry(this, createdAt);
        }
    }
}
