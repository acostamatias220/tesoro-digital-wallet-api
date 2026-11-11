package com.tesoro.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.tesoro.application.command.RegisterCommand;
import com.tesoro.application.model.AuthTokens;
import com.tesoro.application.port.in.AuthUseCase;
import com.tesoro.support.Api;
import com.tesoro.support.IntegrationTest;
import com.tesoro.support.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
        properties = {
            "tesoro.rate-limit.enabled=true",
            "tesoro.rate-limit.auth-per-minute=5",
            "tesoro.rate-limit.operations-per-minute=3",
            "tesoro.rate-limit.default-per-minute=1000"
        })
class RateLimitIT extends IntegrationTest {

    @Autowired
    private AuthUseCase auth;

    @Test
    void loginAttemptsAreLimitedPerClient() {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            statuses.add(api.post(
                            "/api/v1/auth/login",
                            null,
                            null,
                            "{\"email\": \"x@example.com\", \"password\": \"incorrecta-123\"}")
                    .status());
        }
        assertThat(statuses).containsExactly(401, 401, 401, 401, 401, 429, 429);

        Api.Response limited =
                api.post("/api/v1/auth/login", null, null, "{\"email\": \"x@example.com\", \"password\": \"x\"}");
        limited.expectProblem(429, "rate-limited");
        assertThat(Long.parseLong(limited.header("Retry-After"))).isPositive();
    }

    @Test
    void moneyMovementsAreLimitedPerUser() {
        // Users are created through the use case so this test does not consume the auth bucket.
        Session ana = registerDirectly("ana");
        Session bruno = registerDirectly("bruno");

        for (int i = 0; i < 3; i++) {
            ana.deposit("ARS", "1").expectStatus(201);
        }
        ana.deposit("ARS", "1").expectProblem(429, "rate-limited");
        // Another user has their own bucket, and reads are not affected.
        bruno.deposit("ARS", "1").expectStatus(201);
        ana.get("/api/v1/accounts").expectStatus(200);
        assertThat(ana.get("/api/v1/accounts").header("X-RateLimit-Limit")).isEqualTo("1000");
    }

    private Session registerDirectly(String name) {
        String email = name + "-" + UUID.randomUUID() + "@example.com";
        AuthTokens tokens = auth.register(new RegisterCommand(email, PASSWORD, name));
        return new Session(api, email, tokens.accessToken(), tokens.refreshToken());
    }
}
