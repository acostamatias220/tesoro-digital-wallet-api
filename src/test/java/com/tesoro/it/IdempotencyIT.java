package com.tesoro.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.tesoro.support.IntegrationTest;
import com.tesoro.support.Session;
import org.junit.jupiter.api.Test;

class IdempotencyIT extends IntegrationTest {

    @Test
    void retryingWithTheSameKeyReplaysTheOriginalResponse() {
        Session ana = register("Ana");
        Session bruno = register("Bruno");
        ana.deposit("ARS", "1000").expectStatus(201);
        String key = newKey();

        var first = ana.transfer(bruno.email(), "ARS", "250", key).expectStatus(201);
        var retry = ana.transfer(bruno.email(), "ARS", "250", key).expectStatus(201);

        assertThat(first.header("Idempotent-Replayed")).isEqualTo("false");
        assertThat(retry.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(retry.body()).isEqualTo(first.body());
        assertThat(retry.header("Location")).isEqualTo(first.header("Location"));
        assertThat(ana.balance("ARS")).isEqualByComparingTo("750");
        assertThat(bruno.balance("ARS")).isEqualByComparingTo("250");
    }

    @Test
    void reusingAKeyWithADifferentBodyIsRejected() {
        Session ana = register("Ana");
        String key = newKey();
        ana.post("/api/v1/deposits", key, "{\"currency\": \"ARS\", \"amount\": 100}")
                .expectStatus(201);

        ana.post("/api/v1/deposits", key, "{\"currency\": \"ARS\", \"amount\": 999}")
                .expectProblem(422, "idempotency-key-reused");
        // Same key on another endpoint is a different request too.
        ana.withdraw("ARS", "100", key).expectProblem(422, "idempotency-key-reused");
        assertThat(ana.balance("ARS")).isEqualByComparingTo("100");
    }

    @Test
    void keysAreScopedPerUser() {
        Session ana = register("Ana");
        Session bruno = register("Bruno");
        String key = newKey();

        ana.post("/api/v1/deposits", key, "{\"currency\": \"USD\", \"amount\": 10}")
                .expectStatus(201);
        JsonNode brunos = bruno.post("/api/v1/deposits", key, "{\"currency\": \"USD\", \"amount\": 10}")
                .expectStatus(201)
                .body();

        assertThat(bruno.balance("USD")).isEqualByComparingTo("10");
        assertThat(brunos.get("walletChanges").get(0).get("accountId").asText()).isEqualTo(bruno.walletId("USD"));
    }

    @Test
    void aFailedOperationDoesNotBurnTheKey() {
        Session ana = register("Ana");
        String key = newKey();

        ana.withdraw("ARS", "50", key).expectProblem(422, "insufficient-funds");
        ana.deposit("ARS", "50").expectStatus(201);

        // The failed attempt rolled back together with its key, so the client can retry it.
        ana.withdraw("ARS", "50", key).expectStatus(201).body();
        assertThat(ana.balance("ARS")).isZero();
    }
}
