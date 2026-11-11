package com.tesoro.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.tesoro.application.service.UserProvisioningService;
import com.tesoro.domain.model.Role;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests: the full application on a random port, talking to a real
 * PostgreSQL database over HTTP. The database comes from TEST_DB_URL / TEST_DB_USER /
 * TEST_DB_PASSWORD (a PostgreSQL service container in CI); Flyway migrates it on startup and every
 * test starts from empty tables.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    /** Random per JVM: no signing secret is ever stored in the repository, not even for tests. */
    private static final String JWT_SECRET = randomHex(32);

    protected static final String PASSWORD = "contraseña-de-prueba-123";

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserProvisioningService provisioning;

    protected Api api;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("TEST_DB_URL", "jdbc:postgresql://localhost:5432/tesoro_test"));
        registry.add("spring.datasource.username", () -> env("TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> env("TEST_DB_PASSWORD", ""));
        registry.add("tesoro.security.jwt.secret", () -> JWT_SECRET);
    }

    @BeforeEach
    void resetDatabase() {
        // TRUNCATE does not fire the row-level append-only triggers; system accounts are kept.
        jdbc.execute("TRUNCATE postings, journal_entries, idempotency_keys, outbox_events, audit_log,"
                + " reconciliation_runs, refresh_tokens");
        jdbc.execute("DELETE FROM accounts WHERE kind = 'USER_WALLET'");
        jdbc.execute("DELETE FROM users");
        api = new Api("http://localhost:" + port);
    }

    /** Registers a user through the API and returns a client authenticated as that user. */
    protected Session register(String name) {
        String email = name.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode tokens = api.post("/api/v1/auth/register", null, null, """
                        {"email": "%s", "password": "%s", "fullName": "%s"}
                        """.formatted(email, PASSWORD, name))
                .expectStatus(201)
                .body();
        return new Session(
                api,
                email,
                tokens.get("accessToken").asText(),
                tokens.get("refreshToken").asText());
    }

    protected Session admin() {
        String email = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        provisioning.ensureUser(email, PASSWORD, "Admin", Role.ADMIN);
        return login(email);
    }

    protected Session login(String email) {
        JsonNode tokens = api.post(
                        "/api/v1/auth/login",
                        null,
                        null,
                        "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD))
                .expectStatus(200)
                .body();
        return new Session(
                api,
                email,
                tokens.get("accessToken").asText(),
                tokens.get("refreshToken").asText());
    }

    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return HexFormat.of().formatHex(value);
    }
}
