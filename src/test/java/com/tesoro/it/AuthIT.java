package com.tesoro.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.tesoro.support.IntegrationTest;
import com.tesoro.support.Session;
import org.junit.jupiter.api.Test;

class AuthIT extends IntegrationTest {

    @Test
    void registerCreatesTheUserWithEmptyArsAndUsdWalletsAndReturnsTokens() {
        Session ana = register("Ana");

        JsonNode me = ana.get("/api/v1/me").expectStatus(200).body();
        assertThat(me.get("email").asText()).isEqualTo(ana.email());
        assertThat(me.get("role").asText()).isEqualTo("USER");
        assertThat(me.has("passwordHash")).isFalse();

        JsonNode accounts = ana.get("/api/v1/accounts").expectStatus(200).body();
        assertThat(accounts).hasSize(2);
        assertThat(accounts.findValuesAsText("currency")).containsExactly("ARS", "USD");
        assertThat(ana.balance("ARS")).isZero();

        // Stored as a bcrypt hash, never in plain text
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, ana.email());
        assertThat(hash).startsWith("$2a$").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_events WHERE event_type = 'UserRegistered'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsDuplicateEmailsCaseInsensitively() {
        Session ana = register("Ana");
        api.post(
                        "/api/v1/auth/register",
                        null,
                        null,
                        "{\"email\": \"%s\", \"password\": \"%s\", \"fullName\": \"Otra\"}"
                                .formatted(ana.email().toUpperCase(), PASSWORD))
                .expectProblem(409, "email-already-registered");
    }

    @Test
    void validatesTheRegistrationPayload() {
        JsonNode problem = api.post(
                        "/api/v1/auth/register",
                        null,
                        null,
                        "{\"email\": \"no-es-un-email\", \"password\": \"corta\", \"fullName\": \"\"}")
                .expectProblem(400, "validation-failed");
        assertThat(problem.get("errors").findValuesAsText("field"))
                .containsExactlyInAnyOrder("email", "password", "fullName");
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswerAndAreAudited() {
        Session ana = register("Ana");
        api.post(
                        "/api/v1/auth/login",
                        null,
                        null,
                        "{\"email\": \"%s\", \"password\": \"incorrecta-123\"}".formatted(ana.email()))
                .expectProblem(401, "invalid-credentials");
        api.post(
                        "/api/v1/auth/login",
                        null,
                        null,
                        "{\"email\": \"nadie@example.com\", \"password\": \"incorrecta-123\"}")
                .expectProblem(401, "invalid-credentials");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'LOGIN_FAILED'", Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT ip_address FROM audit_log WHERE action = 'LOGIN_FAILED' LIMIT 1", String.class))
                .isNotBlank();
    }

    @Test
    void refreshTokensRotateAndReuseRevokesTheWholeSession() {
        Session ana = register("Ana");

        JsonNode rotated = refresh(ana.refreshToken()).expectStatus(200).body();
        String second = rotated.get("refreshToken").asText();
        assertThat(second).isNotEqualTo(ana.refreshToken());
        assertThat(rotated.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(rotated.get("expiresIn").asLong()).isBetween(1L, 900L);

        // Replaying the first (already used) token looks like theft: the family is revoked...
        refresh(ana.refreshToken()).expectProblem(401, "invalid-refresh-token");
        // ...so even the legitimate newest token stops working.
        refresh(second).expectProblem(401, "invalid-refresh-token");
        // Both rejected attempts are audited.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE action = 'REFRESH_TOKEN_REUSED'", Long.class))
                .isEqualTo(2);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        Session ana = register("Ana");
        api.post("/api/v1/auth/logout", null, null, "{\"refreshToken\": \"%s\"}".formatted(ana.refreshToken()))
                .expectStatus(204);
        refresh(ana.refreshToken()).expectProblem(401, "invalid-refresh-token");
    }

    @Test
    void protectedEndpointsRequireAValidToken() {
        api.get("/api/v1/accounts", null).expectProblem(401, "unauthorized");
        api.get("/api/v1/accounts", "not-a-jwt").expectProblem(401, "unauthorized");

        Session ana = register("Ana");
        String tampered = ana.accessToken().substring(0, ana.accessToken().length() - 4) + "AAAA";
        api.get("/api/v1/accounts", tampered).expectProblem(401, "unauthorized");
    }

    private com.tesoro.support.Api.Response refresh(String token) {
        return api.post("/api/v1/auth/refresh", null, null, "{\"refreshToken\": \"%s\"}".formatted(token));
    }
}
