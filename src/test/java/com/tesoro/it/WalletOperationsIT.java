package com.tesoro.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.tesoro.support.IntegrationTest;
import com.tesoro.support.Session;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WalletOperationsIT extends IntegrationTest {

    @Test
    void depositCreditsTheWalletAndWritesABalancedEntry() {
        Session ana = register("Ana");

        JsonNode movement = ana.deposit("ARS", "15000.50").expectStatus(201).body();

        assertThat(movement.get("type").asText()).isEqualTo("DEPOSIT");
        JsonNode change = movement.get("walletChanges").get(0);
        assertThat(change.get("direction").asText()).isEqualTo("CREDIT");
        assertThat(change.get("balanceAfter").decimalValue()).isEqualByComparingTo("15000.50");
        assertThat(ana.balance("ARS")).isEqualByComparingTo("15000.50");

        JsonNode entry = ana.get(
                        "/api/v1/transactions/" + movement.get("transactionId").asText())
                .expectStatus(200)
                .body();
        assertThat(entry.get("postings")).hasSize(2);
        assertThat(entry.get("postings").findValuesAsText("direction")).containsExactly("DEBIT", "CREDIT");
        assertThat(entry.get("postings").get(0).get("account").asText()).isEqualTo("SETTLEMENT_ARS");
    }

    @Test
    void withdrawalNeedsFundsAndFailsAtomically() {
        Session ana = register("Ana");
        ana.deposit("USD", "100").expectStatus(201);

        JsonNode problem = ana.withdraw("USD", "100.01", newKey()).expectProblem(422, "insufficient-funds");
        assertThat(problem.get("detail").asText()).contains("disponible 100.00 USD");
        assertThat(ana.balance("USD")).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_entries WHERE type = 'WITHDRAWAL'", Long.class))
                .isZero();

        ana.withdraw("USD", "100", newKey()).expectStatus(201);
        assertThat(ana.balance("USD")).isZero();
    }

    @Test
    void transferMovesMoneyBetweenUsersWithoutLeakingTheRecipientBalance() {
        Session ana = register("Ana");
        Session bruno = register("Bruno");
        ana.deposit("ARS", "5000").expectStatus(201);

        JsonNode result = ana.post("/api/v1/transfers", newKey(), """
                        {"recipientEmail": "%s", "currency": "ARS", "amount": 1234.56, "description": "Asado"}
                        """.formatted(
                                bruno.email().toUpperCase()))
                .expectStatus(201)
                .body();

        assertThat(result.get("description").asText()).isEqualTo("Asado");
        assertThat(result.get("walletChanges")).hasSize(1);
        assertThat(result.get("details").get("recipientName").asText()).isEqualTo("Bruno");
        assertThat(ana.balance("ARS")).isEqualByComparingTo("3765.44");
        assertThat(bruno.balance("ARS")).isEqualByComparingTo("1234.56");

        // Both participants can see the entry; a third user cannot even know it exists.
        String transactionId = result.get("transactionId").asText();
        bruno.get("/api/v1/transactions/" + transactionId).expectStatus(200);
        register("Carla").get("/api/v1/transactions/" + transactionId).expectProblem(404, "not-found");
    }

    @Test
    void transferRulesAreEnforced() {
        Session ana = register("Ana");
        ana.deposit("ARS", "100").expectStatus(201);

        ana.transfer(ana.email(), "ARS", "10", newKey()).expectProblem(422, "same-account-transfer");
        ana.transfer("nadie@example.com", "ARS", "10", newKey()).expectProblem(404, "not-found");
        ana.transfer(register("Bruno").email(), "ARS", "100.01", newKey()).expectProblem(422, "insufficient-funds");
    }

    @Test
    void conversionUsesTheSpreadAndRoundsDownInBothLegs() {
        Session ana = register("Ana");
        ana.deposit("ARS", "100000").expectStatus(201);

        JsonNode result =
                ana.convert("ARS", "USD", "14500", newKey()).expectStatus(201).body();

        // 14 500 ARS / 1 450 (ask) = 10 USD
        assertThat(ana.balance("ARS")).isEqualByComparingTo("85500");
        assertThat(ana.balance("USD")).isEqualByComparingTo("10");
        assertThat(result.get("walletChanges")).hasSize(2);
        assertThat(result.get("details").get("rateSource").asText()).isEqualTo("mock");

        // 1 000 ARS / 1 450 = 0.6896... -> 0.68 USD (rounded down)
        ana.convert("ARS", "USD", "1000", newKey()).expectStatus(201);
        assertThat(ana.balance("USD")).isEqualByComparingTo("10.68");

        // Back to ARS at the bid: 10 USD * 1 400 = 14 000 ARS
        ana.convert("USD", "ARS", "10", newKey()).expectStatus(201);
        assertThat(ana.balance("ARS")).isEqualByComparingTo("98500");

        JsonNode entry = ana.get(
                        "/api/v1/transactions/" + result.get("transactionId").asText())
                .expectStatus(200)
                .body();
        assertThat(entry.get("postings")).hasSize(4);
        assertThat(entry.get("metadata").get("ask").asText()).isEqualTo("1450.00");
    }

    @Test
    void quotePreviewsAConversionWithoutMovingMoney() {
        Session ana = register("Ana");
        JsonNode quote = ana.get("/api/v1/fx/quote?from=USD&to=ARS&amount=2.5")
                .expectStatus(200)
                .body();
        assertThat(quote.get("convertedAmount").decimalValue()).isEqualByComparingTo("3500");
        assertThat(quote.get("bid").decimalValue()).isEqualByComparingTo("1400");

        ana.get("/api/v1/fx/quote?from=USD&to=USD&amount=1").expectProblem(400, "invalid-amount");
        ana.get("/api/v1/fx/quote?from=USD&to=EUR&amount=1").expectProblem(400, "invalid-parameter");
    }

    @Test
    void validatesAmountsAndHeaders() {
        Session ana = register("Ana");
        ana.post("/api/v1/deposits", newKey(), "{\"currency\": \"ARS\", \"amount\": 10.001}")
                .expectProblem(400, "validation-failed");
        ana.post("/api/v1/deposits", newKey(), "{\"currency\": \"ARS\", \"amount\": -5}")
                .expectProblem(400, "validation-failed");
        ana.post("/api/v1/deposits", newKey(), "{\"currency\": \"EUR\", \"amount\": 5}")
                .expectStatus(400);
        ana.post("/api/v1/deposits", null, "{\"currency\": \"ARS\", \"amount\": 5}")
                .expectProblem(400, "missing-header");
        ana.post("/api/v1/deposits", "corta", "{\"currency\": \"ARS\", \"amount\": 5}")
                .expectProblem(400, "validation-failed");
    }

    @Test
    void statementIsPaginatedFilteredAndCarriesRunningBalances() {
        Session ana = register("Ana");
        Session bruno = register("Bruno");
        ana.deposit("ARS", "1000").expectStatus(201);
        ana.deposit("ARS", "500").expectStatus(201);
        ana.transfer(bruno.email(), "ARS", "300", newKey()).expectStatus(201);
        ana.withdraw("ARS", "200", newKey()).expectStatus(201);

        String path = "/api/v1/accounts/" + ana.walletId("ARS") + "/movements";
        JsonNode page = ana.get(path + "?size=3").expectStatus(200).body();
        assertThat(page.get("totalElements").asInt()).isEqualTo(4);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("content").findValuesAsText("type")).containsExactly("WITHDRAWAL", "TRANSFER", "DEPOSIT");
        assertThat(page.get("content").findValues("balanceAfter"))
                .extracting(JsonNode::decimalValue)
                .containsExactly(new BigDecimal("1000.00"), new BigDecimal("1200.00"), new BigDecimal("1500.00"));

        JsonNode deposits = ana.get(path + "?type=DEPOSIT").expectStatus(200).body();
        assertThat(deposits.get("totalElements").asInt()).isEqualTo(2);
        JsonNode debits = ana.get(path + "?direction=DEBIT").expectStatus(200).body();
        assertThat(debits.get("totalElements").asInt()).isEqualTo(2);
        JsonNode future =
                ana.get(path + "?from=2999-01-01T00:00:00Z").expectStatus(200).body();
        assertThat(future.get("totalElements").asInt()).isZero();
        ana.get(path + "?size=1000").expectProblem(400, "validation-failed");

        // Someone else's account is reported as non-existent.
        bruno.get(path).expectProblem(404, "not-found");
    }

    @Test
    void frozenWalletsCannotMoveMoney() {
        Session ana = register("Ana");
        Session bruno = register("Bruno");
        ana.deposit("ARS", "100").expectStatus(201);
        jdbc.update("UPDATE accounts SET status = 'FROZEN' WHERE id = ?::uuid", bruno.walletId("ARS"));

        ana.transfer(bruno.email(), "ARS", "10", newKey()).expectProblem(422, "account-frozen");
        bruno.deposit("ARS", "10").expectProblem(422, "account-frozen");
        assertThat(ana.balance("ARS")).isEqualByComparingTo("100");
    }
}
