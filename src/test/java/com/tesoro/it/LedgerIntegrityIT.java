package com.tesoro.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.tesoro.support.IntegrationTest;
import com.tesoro.support.Session;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

/** The database enforces the ledger rules on its own, and the reconciliation catches tampering. */
class LedgerIntegrityIT extends IntegrationTest {

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void postingsAndEntriesAreAppendOnly() {
        Session ana = register("Ana");
        ana.deposit("ARS", "100").expectStatus(201);

        assertThatThrownBy(() -> jdbc.update("UPDATE postings SET amount = amount + 1"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM journal_entries"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void anUnbalancedEntryIsRejectedAtCommit() {
        Session ana = register("Ana");
        String walletId = ana.walletId("ARS");
        UUID entryId = UUID.randomUUID();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    jdbc.update(
                            "INSERT INTO journal_entries (id, type, description, created_at) VALUES (?, 'DEPOSIT', 'x', now())",
                            entryId);
                    jdbc.update(
                            "INSERT INTO postings (journal_entry_id, account_id, currency, direction, amount, created_at)"
                                    + " VALUES (?, '00000000-0000-0000-0000-00000000a001', 'ARS', 'DEBIT', 100, now())",
                            entryId);
                    jdbc.update(
                            "INSERT INTO postings (journal_entry_id, account_id, currency, direction, amount, created_at)"
                                    + " VALUES (?, ?::uuid, 'ARS', 'CREDIT', 99, now())",
                            entryId,
                            walletId);
                }))
                .hasMessageContaining("does not balance");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_entries", Long.class))
                .isZero();
    }

    @Test
    void postingCurrencyMustMatchTheAccountAndWalletsCannotGoNegative() {
        Session ana = register("Ana");

        assertThatThrownBy(
                        () -> jdbc.update("UPDATE accounts SET balance = -1 WHERE id = ?::uuid", ana.walletId("ARS")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_accounts_wallet_non_negative");

        UUID entryId = UUID.randomUUID();
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    jdbc.update(
                            "INSERT INTO journal_entries (id, type, description, created_at) VALUES (?, 'DEPOSIT', 'x', now())",
                            entryId);
                    jdbc.update(
                            "INSERT INTO postings (journal_entry_id, account_id, currency, direction, amount, created_at)"
                                    + " VALUES (?, ?::uuid, 'USD', 'CREDIT', 1, now())",
                            entryId,
                            ana.walletId("ARS"));
                }))
                .hasMessageContaining("fk_postings_account_currency");
    }

    @Test
    void reconciliationPassesOnAHealthyLedgerAndDetectsATamperedBalance() {
        Session ana = register("Ana");
        Session bruno = register("Bruno");
        Session admin = admin();
        ana.deposit("ARS", "1000").expectStatus(201);
        ana.transfer(bruno.email(), "ARS", "250", newKey()).expectStatus(201);
        ana.convert("ARS", "USD", "145", newKey()).expectStatus(201);

        JsonNode ok = admin.post("/api/v1/admin/reconciliations", null, null)
                .expectStatus(200)
                .body();
        assertThat(ok.get("status").asText()).isEqualTo("OK");
        assertThat(ok.get("entriesChecked").asInt()).isEqualTo(3);
        assertThat(ok.get("walletsChecked").asInt()).isEqualTo(6);

        // Someone edits a cached balance directly in the database...
        jdbc.update("UPDATE accounts SET balance = balance + 500 WHERE id = ?::uuid", bruno.walletId("ARS"));

        JsonNode mismatch = admin.post("/api/v1/admin/reconciliations", null, null)
                .expectStatus(200)
                .body();
        assertThat(mismatch.get("status").asText()).isEqualTo("MISMATCH");
        JsonNode issue = mismatch.get("issues").get(0);
        assertThat(issue.get("type").asText()).isEqualTo("BALANCE_MISMATCH");
        assertThat(issue.get("reference").asText()).isEqualTo(bruno.walletId("ARS"));
        assertThat(new BigDecimal(issue.get("expected").asText())).isEqualByComparingTo("250");
        assertThat(new BigDecimal(issue.get("actual").asText())).isEqualByComparingTo("750");
        assertThat(meterRegistry.get("tesoro.reconciliation.issues").gauge().value())
                .isEqualTo(1.0);

        JsonNode history =
                admin.get("/api/v1/admin/reconciliations").expectStatus(200).body();
        assertThat(history.get("content").findValuesAsText("status")).containsExactly("MISMATCH", "OK");
    }

    @Test
    void systemAccountBalancesAreDerivedFromPostings() {
        Session ana = register("Ana");
        Session admin = admin();
        ana.deposit("ARS", "2900").expectStatus(201);
        ana.withdraw("ARS", "400", newKey()).expectStatus(201);
        ana.convert("ARS", "USD", "1450", newKey()).expectStatus(201);

        JsonNode accounts = admin.get("/api/v1/admin/ledger/system-accounts")
                .expectStatus(200)
                .body();
        // Bank funds: 2 900 in - 400 out. FX pools: the platform received 1 450 ARS and gave 1 USD.
        assertThat(balanceOf(accounts, "SETTLEMENT_ARS")).isEqualByComparingTo("2500");
        assertThat(balanceOf(accounts, "FX_POOL_ARS")).isEqualByComparingTo("1450");
        assertThat(balanceOf(accounts, "FX_POOL_USD")).isEqualByComparingTo("-1");
        assertThat(balanceOf(accounts, "SETTLEMENT_USD")).isZero();
    }

    private static BigDecimal balanceOf(JsonNode accounts, String code) {
        for (JsonNode account : accounts) {
            if (account.get("code").asText().equals(code)) {
                return account.get("balance").decimalValue();
            }
        }
        throw new AssertionError(code);
    }
}
