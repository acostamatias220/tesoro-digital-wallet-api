package com.tesoro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tesoro.application.command.DepositCommand;
import com.tesoro.application.command.TransferCommand;
import com.tesoro.application.command.WithdrawCommand;
import com.tesoro.application.model.IdempotentResult;
import com.tesoro.application.model.MovementResult;
import com.tesoro.application.port.out.AccountRepository;
import com.tesoro.application.port.out.AuditLog;
import com.tesoro.application.port.out.ExchangeRateProvider;
import com.tesoro.application.port.out.IdempotencyStore;
import com.tesoro.application.port.out.LedgerRepository;
import com.tesoro.application.port.out.OutboxRepository;
import com.tesoro.application.port.out.UserRepository;
import com.tesoro.application.service.IdempotencyGuard;
import com.tesoro.application.service.WalletOperationsService;
import com.tesoro.domain.event.DomainEvent;
import com.tesoro.domain.exception.InsufficientFundsException;
import com.tesoro.domain.exception.SameAccountTransferException;
import com.tesoro.domain.ledger.EntryType;
import com.tesoro.domain.ledger.JournalEntry;
import com.tesoro.domain.model.Account;
import com.tesoro.domain.model.AccountKind;
import com.tesoro.domain.model.AccountStatus;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Money;
import com.tesoro.domain.model.Role;
import com.tesoro.domain.model.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Orchestration rules of the wallet use cases, with every port mocked. */
class WalletOperationsServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LedgerRepository ledger = mock(LedgerRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final AuditLog auditLog = mock(AuditLog.class);
    private final IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private WalletOperationsService service;

    private final UUID aliceId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private final UUID bobId = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private Account aliceArs;
    private Account bobArs;
    private final Account settlementArs = system(AccountKind.SETTLEMENT);

    @BeforeEach
    void setUp() {
        service = new WalletOperationsService(
                accounts,
                users,
                ledger,
                outbox,
                auditLog,
                mock(ExchangeRateProvider.class),
                new IdempotencyGuard(idempotencyStore),
                meters,
                Clock.fixed(NOW, ZoneOffset.UTC));
        // Ids chosen so that Bob's wallet sorts BEFORE Alice's.
        aliceArs = wallet("ffffffff-0000-0000-0000-000000000001", aliceId, "500");
        bobArs = wallet("00000000-0000-0000-0000-000000000002", bobId, "0");
        when(idempotencyStore.claim(any(), anyString(), anyString(), eq(MovementResult.class)))
                .thenReturn(Optional.empty());
        when(accounts.findWallet(aliceId, CurrencyCode.ARS)).thenReturn(Optional.of(aliceArs));
        when(accounts.findWallet(bobId, CurrencyCode.ARS)).thenReturn(Optional.of(bobArs));
        when(accounts.lockForUpdate(aliceArs.id())).thenReturn(aliceArs);
        when(accounts.lockForUpdate(bobArs.id())).thenReturn(bobArs);
        when(accounts.getSystemAccount(AccountKind.SETTLEMENT, CurrencyCode.ARS))
                .thenReturn(settlementArs);
        when(users.findByEmail("bob@example.com"))
                .thenReturn(Optional.of(new User(bobId, "bob@example.com", "hash", "Bob", Role.USER, true, NOW)));
    }

    @Test
    void transferLocksWalletsInGlobalOrderRegardlessOfDirection() {
        service.transfer(new TransferCommand(
                aliceId, "key-00000001", "bob@example.com", Money.of("100", CurrencyCode.ARS), null));

        var order = inOrder(accounts);
        order.verify(accounts).lockForUpdate(bobArs.id());
        order.verify(accounts).lockForUpdate(aliceArs.id());
        assertThat(aliceArs.balance()).isEqualTo(Money.of("400", CurrencyCode.ARS));
        assertThat(bobArs.balance()).isEqualTo(Money.of("100", CurrencyCode.ARS));
    }

    @Test
    void transferRecordsBalancedEntryEventAuditAndStoresTheIdempotentResponse() {
        IdempotentResult<MovementResult> result = service.transfer(new TransferCommand(
                aliceId, "key-00000002", "bob@example.com", Money.of("100", CurrencyCode.ARS), "Regalo"));

        ArgumentCaptor<JournalEntry> entry = ArgumentCaptor.forClass(JournalEntry.class);
        verify(ledger).append(entry.capture(), anyMap());
        assertThat(entry.getValue().type()).isEqualTo(EntryType.TRANSFER);
        assertThat(entry.getValue().totalDebits(CurrencyCode.ARS)).isEqualTo(Money.of("100", CurrencyCode.ARS));
        verify(outbox).enqueue(any(DomainEvent.class));
        verify(auditLog).record(any());
        verify(idempotencyStore).complete(aliceId, "key-00000002", result.value());
        assertThat(result.replayed()).isFalse();
        assertThat(result.value().walletChanges()).singleElement().satisfies(change -> {
            assertThat(change.accountId()).isEqualTo(aliceArs.id());
            assertThat(change.balanceAfter()).isEqualByComparingTo("400");
        });
        assertThat(meters.counter("tesoro.movements", "type", "TRANSFER", "currency", "ARS")
                        .count())
                .isEqualTo(1);
    }

    @Test
    void insufficientFundsAbortsBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> service.withdraw(
                        new WithdrawCommand(aliceId, "key-00000003", Money.of("500.01", CurrencyCode.ARS))))
                .isInstanceOf(InsufficientFundsException.class);

        verify(ledger, never()).append(any(), anyMap());
        verify(outbox, never()).enqueue(any());
        verify(idempotencyStore, never()).complete(any(), anyString(), any());
    }

    @Test
    void transferToYourselfIsRejected() {
        when(users.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(new User(aliceId, "alice@example.com", "hash", "Alice", Role.USER, true, NOW)));

        assertThatThrownBy(() -> service.transfer(new TransferCommand(
                        aliceId, "key-00000004", "Alice@Example.com", Money.of("1", CurrencyCode.ARS), null)))
                .isInstanceOf(SameAccountTransferException.class);
        verify(accounts, never()).lockForUpdate(any());
    }

    @Test
    void aReplayedKeyReturnsTheStoredResultWithoutTouchingTheLedger() {
        MovementResult stored = new MovementResult(
                UUID.randomUUID(), EntryType.DEPOSIT, "Depósito", NOW, java.util.List.of(), java.util.Map.of());
        when(idempotencyStore.claim(eq(aliceId), eq("key-00000005"), anyString(), eq(MovementResult.class)))
                .thenReturn(Optional.of(stored));

        var result = service.deposit(new DepositCommand(aliceId, "key-00000005", Money.of("10", CurrencyCode.ARS)));

        assertThat(result.replayed()).isTrue();
        assertThat(result.value()).isSameAs(stored);
        verify(accounts, never()).lockForUpdate(any());
        verify(ledger, never()).append(any(), anyMap());
    }

    private static Account wallet(String id, UUID owner, String balance) {
        return new Account(
                UUID.fromString(id),
                owner,
                null,
                CurrencyCode.ARS,
                AccountKind.USER_WALLET,
                AccountStatus.ACTIVE,
                Money.of(balance, CurrencyCode.ARS),
                NOW);
    }

    private static Account system(AccountKind kind) {
        return new Account(
                UUID.randomUUID(),
                null,
                kind + "_ARS",
                CurrencyCode.ARS,
                kind,
                AccountStatus.ACTIVE,
                Money.zero(CurrencyCode.ARS),
                NOW);
    }
}
