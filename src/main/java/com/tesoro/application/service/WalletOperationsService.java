package com.tesoro.application.service;

import com.tesoro.application.command.ConvertCommand;
import com.tesoro.application.command.DepositCommand;
import com.tesoro.application.command.TransferCommand;
import com.tesoro.application.command.WithdrawCommand;
import com.tesoro.application.exception.NotFoundException;
import com.tesoro.application.model.AuditEntry;
import com.tesoro.application.model.IdempotentResult;
import com.tesoro.application.model.MovementResult;
import com.tesoro.application.model.WalletChange;
import com.tesoro.application.port.in.WalletOperationsUseCase;
import com.tesoro.application.port.out.AccountRepository;
import com.tesoro.application.port.out.AuditLog;
import com.tesoro.application.port.out.ExchangeRateProvider;
import com.tesoro.application.port.out.LedgerRepository;
import com.tesoro.application.port.out.OutboxRepository;
import com.tesoro.application.port.out.UserRepository;
import com.tesoro.domain.event.CurrencyConverted;
import com.tesoro.domain.event.DomainEvent;
import com.tesoro.domain.event.MoneyDeposited;
import com.tesoro.domain.event.MoneyWithdrawn;
import com.tesoro.domain.event.TransferCompleted;
import com.tesoro.domain.exception.SameAccountTransferException;
import com.tesoro.domain.exchange.Conversion;
import com.tesoro.domain.exchange.CurrencyConverter;
import com.tesoro.domain.exchange.ExchangeQuote;
import com.tesoro.domain.ledger.EntryType;
import com.tesoro.domain.ledger.JournalEntry;
import com.tesoro.domain.model.Account;
import com.tesoro.domain.model.AccountKind;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Direction;
import com.tesoro.domain.model.Money;
import com.tesoro.domain.model.User;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deposits, withdrawals, transfers and conversions. Every operation runs in one database
 * transaction that: claims the idempotency key, locks the user wallets involved in a stable order,
 * applies the domain rules, appends a balanced journal entry, updates the cached balances and
 * writes the outbox event and the audit entry.
 */
@Service
public class WalletOperationsService implements WalletOperationsUseCase {

    /** Global lock order: always by the textual UUID, the same order PostgreSQL uses for uuid. */
    static final Comparator<UUID> LOCK_ORDER = Comparator.comparing(UUID::toString);

    private final AccountRepository accounts;
    private final UserRepository users;
    private final LedgerRepository ledger;
    private final OutboxRepository outbox;
    private final AuditLog auditLog;
    private final ExchangeRateProvider exchangeRates;
    private final IdempotencyGuard idempotency;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public WalletOperationsService(
            AccountRepository accounts,
            UserRepository users,
            LedgerRepository ledger,
            OutboxRepository outbox,
            AuditLog auditLog,
            ExchangeRateProvider exchangeRates,
            IdempotencyGuard idempotency,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.accounts = accounts;
        this.users = users;
        this.ledger = ledger;
        this.outbox = outbox;
        this.auditLog = auditLog;
        this.exchangeRates = exchangeRates;
        this.idempotency = idempotency;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IdempotentResult<MovementResult> deposit(DepositCommand command) {
        return idempotency.execute(
                command.userId(),
                command.idempotencyKey(),
                command.fingerprint(),
                MovementResult.class,
                () -> doDeposit(command));
    }

    @Override
    @Transactional
    public IdempotentResult<MovementResult> withdraw(WithdrawCommand command) {
        return idempotency.execute(
                command.userId(),
                command.idempotencyKey(),
                command.fingerprint(),
                MovementResult.class,
                () -> doWithdraw(command));
    }

    @Override
    @Transactional
    public IdempotentResult<MovementResult> transfer(TransferCommand command) {
        return idempotency.execute(
                command.userId(),
                command.idempotencyKey(),
                command.fingerprint(),
                MovementResult.class,
                () -> doTransfer(command));
    }

    @Override
    @Transactional
    public IdempotentResult<MovementResult> convert(ConvertCommand command) {
        return idempotency.execute(
                command.userId(),
                command.idempotencyKey(),
                command.fingerprint(),
                MovementResult.class,
                () -> doConvert(command));
    }

    private MovementResult doDeposit(DepositCommand command) {
        Money amount = command.amount();
        UUID walletId = walletOf(command.userId(), amount.currency()).id();
        Account wallet = lockInOrder(walletId).get(walletId);
        Account settlement = accounts.getSystemAccount(AccountKind.SETTLEMENT, amount.currency());

        // Bank funds (asset) go up, what we owe the user (liability) goes up.
        settlement.debit(amount);
        wallet.credit(amount);
        Instant now = clock.instant();
        JournalEntry entry = JournalEntry.builder(EntryType.DEPOSIT, "Depósito")
                .initiatedBy(command.userId())
                .debit(settlement, amount)
                .credit(wallet, amount)
                .build(now);

        record(
                entry,
                List.of(wallet),
                new MoneyDeposited(
                        entry.id(),
                        wallet.id(),
                        amount.amount(),
                        amount.currency().name(),
                        now));
        return result(entry, List.of(wallet), Map.of());
    }

    private MovementResult doWithdraw(WithdrawCommand command) {
        Money amount = command.amount();
        UUID walletId = walletOf(command.userId(), amount.currency()).id();
        Account wallet = lockInOrder(walletId).get(walletId);
        Account settlement = accounts.getSystemAccount(AccountKind.SETTLEMENT, amount.currency());

        wallet.debit(amount); // throws InsufficientFundsException, the whole transaction rolls back
        settlement.credit(amount);
        Instant now = clock.instant();
        JournalEntry entry = JournalEntry.builder(EntryType.WITHDRAWAL, "Extracción")
                .initiatedBy(command.userId())
                .debit(wallet, amount)
                .credit(settlement, amount)
                .build(now);

        record(
                entry,
                List.of(wallet),
                new MoneyWithdrawn(
                        entry.id(),
                        wallet.id(),
                        amount.amount(),
                        amount.currency().name(),
                        now));
        return result(entry, List.of(wallet), Map.of());
    }

    private MovementResult doTransfer(TransferCommand command) {
        Money amount = command.amount();
        User recipient = users.findByEmail(User.normalizeEmail(command.recipientEmail()))
                .filter(User::enabled)
                .orElseThrow(() -> new NotFoundException("No existe un usuario activo con ese email"));
        UUID fromId = walletOf(command.userId(), amount.currency()).id();
        UUID toId = walletOf(recipient.id(), amount.currency()).id();
        if (fromId.equals(toId)) {
            throw new SameAccountTransferException();
        }

        Map<UUID, Account> locked = lockInOrder(fromId, toId);
        Account from = locked.get(fromId);
        Account to = locked.get(toId);
        from.debit(amount);
        to.credit(amount);

        Instant now = clock.instant();
        String description =
                command.description() == null || command.description().isBlank()
                        ? "Transferencia a " + recipient.fullName()
                        : command.description().strip();
        JournalEntry entry = JournalEntry.builder(EntryType.TRANSFER, description)
                .initiatedBy(command.userId())
                .metadata("fromUserId", command.userId().toString())
                .metadata("toUserId", recipient.id().toString())
                .debit(from, amount)
                .credit(to, amount)
                .build(now);

        record(
                entry,
                List.of(from, to),
                new TransferCompleted(
                        entry.id(),
                        from.id(),
                        to.id(),
                        amount.amount(),
                        amount.currency().name(),
                        now));
        // The sender only sees their own wallet, never the recipient's balance.
        return result(
                entry,
                List.of(from),
                Map.of("recipientName", recipient.fullName(), "recipientEmail", recipient.email()));
    }

    private MovementResult doConvert(ConvertCommand command) {
        Money source = command.amount();
        CurrencyCode target = command.targetCurrency();
        ExchangeQuote quote = exchangeRates.currentQuote(CurrencyCode.USD, CurrencyCode.ARS);
        Conversion conversion = CurrencyConverter.convert(source, target, quote);

        UUID sourceId = walletOf(command.userId(), source.currency()).id();
        UUID targetId = walletOf(command.userId(), target).id();
        Map<UUID, Account> locked = lockInOrder(sourceId, targetId);
        Account sourceWallet = locked.get(sourceId);
        Account targetWallet = locked.get(targetId);
        Account sourcePool = accounts.getSystemAccount(AccountKind.FX_POOL, source.currency());
        Account targetPool = accounts.getSystemAccount(AccountKind.FX_POOL, target);

        sourceWallet.debit(source);
        targetWallet.credit(conversion.target());

        Instant now = clock.instant();
        // Two legs, each balanced in its own currency, joined by the FX pool accounts.
        JournalEntry entry = JournalEntry.builder(
                        EntryType.CONVERSION, "Conversión %s → %s".formatted(source.currency(), target))
                .initiatedBy(command.userId())
                .metadata("rate", conversion.rate().toPlainString())
                .metadata("bid", quote.bid().toPlainString())
                .metadata("ask", quote.ask().toPlainString())
                .metadata("rateSource", quote.source())
                .metadata("rateAsOf", quote.asOf().toString())
                .debit(sourceWallet, source)
                .credit(sourcePool, source)
                .debit(targetPool, conversion.target())
                .credit(targetWallet, conversion.target())
                .build(now);

        record(
                entry,
                List.of(sourceWallet, targetWallet),
                new CurrencyConverted(
                        entry.id(),
                        command.userId(),
                        source.amount(),
                        source.currency().name(),
                        conversion.target().amount(),
                        target.name(),
                        conversion.rate(),
                        now));
        return result(
                entry,
                List.of(sourceWallet, targetWallet),
                Map.of("rate", conversion.rate().toPlainString(), "rateSource", quote.source()));
    }

    private Account walletOf(UUID userId, CurrencyCode currency) {
        return accounts.findWallet(userId, currency)
                .orElseThrow(() -> new NotFoundException("No hay una billetera en " + currency));
    }

    /** Locks the given wallets one by one in {@link #LOCK_ORDER} so two transfers never deadlock. */
    private Map<UUID, Account> lockInOrder(UUID... ids) {
        Map<UUID, Account> locked = new LinkedHashMap<>();
        Stream.of(ids).distinct().sorted(LOCK_ORDER).forEach(id -> locked.put(id, accounts.lockForUpdate(id)));
        return locked;
    }

    private void record(JournalEntry entry, List<Account> wallets, DomainEvent event) {
        Map<UUID, Money> balancesAfter = new LinkedHashMap<>();
        wallets.forEach(wallet -> balancesAfter.put(wallet.id(), wallet.balance()));
        ledger.append(entry, balancesAfter);
        wallets.forEach(accounts::update);
        outbox.enqueue(event);
        auditLog.record(AuditEntry.of(
                entry.createdAt(),
                entry.initiatedBy(),
                "MONEY_" + entry.type().name(),
                "JournalEntry",
                entry.id(),
                Map.of("description", entry.description())));
        entry.postings().stream()
                .map(p -> p.amount().currency())
                .distinct()
                .forEach(currency -> meterRegistry
                        .counter("tesoro.movements", "type", entry.type().name(), "currency", currency.name())
                        .increment());
    }

    private static MovementResult result(JournalEntry entry, List<Account> callerWallets, Map<String, String> details) {
        List<WalletChange> changes = callerWallets.stream()
                .map(wallet -> {
                    var posting = entry.postings().stream()
                            .filter(p -> p.accountId().equals(wallet.id()))
                            .findFirst()
                            .orElseThrow();
                    Direction direction = posting.direction();
                    return new WalletChange(
                            wallet.id(),
                            wallet.currency(),
                            direction,
                            posting.amount().amount(),
                            wallet.balance().amount());
                })
                .toList();
        return new MovementResult(entry.id(), entry.type(), entry.description(), entry.createdAt(), changes, details);
    }
}
