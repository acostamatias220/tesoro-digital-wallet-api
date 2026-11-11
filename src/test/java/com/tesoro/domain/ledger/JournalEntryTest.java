package com.tesoro.domain.ledger;

import static com.tesoro.domain.model.CurrencyCode.ARS;
import static com.tesoro.domain.model.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tesoro.domain.exception.CurrencyMismatchException;
import com.tesoro.domain.exception.InvalidAmountException;
import com.tesoro.domain.exception.UnbalancedEntryException;
import com.tesoro.domain.model.Account;
import com.tesoro.domain.model.AccountKind;
import com.tesoro.domain.model.AccountStatus;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Direction;
import com.tesoro.domain.model.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JournalEntryTest {

    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    private final Account aliceArs = Account.openWallet(UUID.randomUUID(), ARS, NOW);
    private final Account aliceUsd = Account.openWallet(aliceArs.ownerId(), USD, NOW);
    private final Account bobArs = Account.openWallet(UUID.randomUUID(), ARS, NOW);
    private final Account fxArs = system(AccountKind.FX_POOL, ARS);
    private final Account fxUsd = system(AccountKind.FX_POOL, USD);

    @Test
    void buildsABalancedTransfer() {
        JournalEntry entry = JournalEntry.builder(EntryType.TRANSFER, "Alquiler")
                .initiatedBy(aliceArs.ownerId())
                .debit(aliceArs, Money.of("1500", ARS))
                .credit(bobArs, Money.of("1500", ARS))
                .build(NOW);

        assertThat(entry.id()).isNotNull();
        assertThat(entry.postings()).hasSize(2);
        assertThat(entry.totalDebits(ARS)).isEqualTo(Money.of("1500", ARS));
        assertThat(entry.postings()).extracting(Posting::direction).containsExactly(Direction.DEBIT, Direction.CREDIT);
        assertThat(entry.createdAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsAnEntryWhoseDebitsAndCreditsDiffer() {
        var builder = JournalEntry.builder(EntryType.TRANSFER, "x")
                .debit(aliceArs, Money.of("100.00", ARS))
                .credit(bobArs, Money.of("99.99", ARS));

        assertThatThrownBy(() -> builder.build(NOW))
                .isInstanceOf(UnbalancedEntryException.class)
                .hasMessageContaining("diferencia de 0.01 ARS");
    }

    @Test
    void conversionMustBalanceInEachCurrencySeparately() {
        JournalEntry conversion = JournalEntry.builder(EntryType.CONVERSION, "ARS a USD")
                .debit(aliceArs, Money.of("14500", ARS))
                .credit(fxArs, Money.of("14500", ARS))
                .debit(fxUsd, Money.of("10", USD))
                .credit(aliceUsd, Money.of("10", USD))
                .metadata("rate", "0.00068965")
                .build(NOW);

        assertThat(conversion.totalDebits(ARS)).isEqualTo(Money.of("14500", ARS));
        assertThat(conversion.totalDebits(USD)).isEqualTo(Money.of("10", USD));
        assertThat(conversion.metadata()).containsEntry("rate", "0.00068965");

        // Same totals but crossing currencies does NOT balance: 14500 ARS are not 10 USD.
        var crossed = JournalEntry.builder(EntryType.CONVERSION, "x")
                .debit(aliceArs, Money.of("10", ARS))
                .credit(aliceUsd, Money.of("10", USD));
        assertThatThrownBy(() -> crossed.build(NOW)).isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void needsAtLeastTwoPostings() {
        assertThatThrownBy(() -> JournalEntry.builder(EntryType.DEPOSIT, "x").build(NOW))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void rejectsZeroAmountsAndPostingsInTheWrongCurrency() {
        var builder = JournalEntry.builder(EntryType.DEPOSIT, "x");
        assertThatThrownBy(() -> builder.debit(aliceArs, Money.zero(ARS))).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> builder.debit(aliceArs, Money.of("1", USD)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void rehydratedEntriesAreVerifiedToo() {
        UUID id = UUID.randomUUID();
        List<Posting> unbalanced = List.of(
                new Posting(aliceArs.id(), Direction.DEBIT, Money.of("5", ARS)),
                new Posting(bobArs.id(), Direction.CREDIT, Money.of("4", ARS)));

        assertThatThrownBy(() -> new JournalEntry(id, EntryType.TRANSFER, "x", null, Map.of(), unbalanced, NOW))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    private static Account system(AccountKind kind, CurrencyCode currency) {
        return new Account(
                UUID.randomUUID(),
                null,
                kind + "_" + currency,
                currency,
                kind,
                AccountStatus.ACTIVE,
                Money.zero(currency),
                NOW);
    }
}
