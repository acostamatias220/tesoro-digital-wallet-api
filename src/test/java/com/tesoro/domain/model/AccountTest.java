package com.tesoro.domain.model;

import static com.tesoro.domain.model.CurrencyCode.ARS;
import static com.tesoro.domain.model.CurrencyCode.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tesoro.domain.exception.AccountFrozenException;
import com.tesoro.domain.exception.CurrencyMismatchException;
import com.tesoro.domain.exception.InsufficientFundsException;
import com.tesoro.domain.exception.InvalidAmountException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountTest {

    private final Account wallet = Account.openWallet(UUID.randomUUID(), ARS, Instant.now());

    @Test
    void walletIsALiabilitySoCreditsIncreaseAndDebitsDecrease() {
        wallet.credit(Money.of("100", ARS));
        wallet.debit(Money.of("30.50", ARS));
        assertThat(wallet.balance()).isEqualTo(Money.of("69.50", ARS));
    }

    @Test
    void walletCannotGoNegative() {
        wallet.credit(Money.of("10", ARS));
        assertThatThrownBy(() -> wallet.debit(Money.of("10.01", ARS)))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("disponible 10.00 ARS");
        assertThat(wallet.balance()).isEqualTo(Money.of("10", ARS));
    }

    @Test
    void frozenWalletRejectsMovements() {
        wallet.credit(Money.of("10", ARS));
        wallet.freeze();
        assertThatThrownBy(() -> wallet.debit(Money.of("1", ARS))).isInstanceOf(AccountFrozenException.class);
        wallet.activate();
        wallet.debit(Money.of("1", ARS));
        assertThat(wallet.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void rejectsNonPositiveAmountsAndOtherCurrencies() {
        assertThatThrownBy(() -> wallet.credit(Money.zero(ARS))).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> wallet.credit(Money.of("1", USD))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void systemAccountsDoNotTrackACachedBalance() {
        Account settlement = new Account(
                UUID.randomUUID(),
                null,
                "SETTLEMENT_ARS",
                ARS,
                AccountKind.SETTLEMENT,
                AccountStatus.ACTIVE,
                Money.zero(ARS),
                Instant.now());
        settlement.credit(Money.of("500", ARS));
        assertThat(settlement.balance().isZero()).isTrue();
        assertThat(AccountKind.SETTLEMENT.normalSide()).isEqualTo(Direction.DEBIT);
    }

    @Test
    void knowsItsOwner() {
        UUID owner = UUID.randomUUID();
        Account own = Account.openWallet(owner, USD, Instant.now());
        assertThat(own.isOwnedBy(owner)).isTrue();
        assertThat(own.isOwnedBy(UUID.randomUUID())).isFalse();
        assertThatThrownBy(() -> new Account(
                        UUID.randomUUID(),
                        null,
                        null,
                        USD,
                        AccountKind.USER_WALLET,
                        AccountStatus.ACTIVE,
                        Money.zero(USD),
                        Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
