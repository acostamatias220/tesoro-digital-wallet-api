package com.tesoro.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tesoro.application.command.DepositCommand;
import com.tesoro.application.command.Fingerprints;
import com.tesoro.application.command.TransferCommand;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FingerprintsTest {

    private final UUID user = UUID.randomUUID();

    @Test
    void sameRequestSameFingerprintRegardlessOfKeyOrNumberFormat() {
        var a = new DepositCommand(user, "key-a", Money.of("10", CurrencyCode.ARS));
        var b = new DepositCommand(user, "key-b", Money.of("10.00", CurrencyCode.ARS));
        assertThat(a.fingerprint()).isEqualTo(b.fingerprint()).hasSize(64);
    }

    @Test
    void anyRelevantChangeChangesTheFingerprint() {
        var base = new TransferCommand(user, "k", "Bob@Example.com", Money.of("10", CurrencyCode.ARS), "x");
        assertThat(base.fingerprint())
                .isEqualTo(new TransferCommand(user, "k", "bob@example.com", Money.of("10", CurrencyCode.ARS), "x")
                        .fingerprint())
                .isNotEqualTo(new TransferCommand(user, "k", "bob@example.com", Money.of("10", CurrencyCode.USD), "x")
                        .fingerprint())
                .isNotEqualTo(new TransferCommand(user, "k", "bob@example.com", Money.of("11", CurrencyCode.ARS), "x")
                        .fingerprint())
                .isNotEqualTo(new TransferCommand(user, "k", "bob@example.com", Money.of("10", CurrencyCode.ARS), "y")
                        .fingerprint());
        assertThat(Fingerprints.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
