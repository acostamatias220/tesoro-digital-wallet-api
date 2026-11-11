package com.tesoro.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesoro.application.exception.IdempotencyKeyReusedException;
import com.tesoro.application.port.out.IdempotencyStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotency keys in PostgreSQL.
 *
 * <p>The claim is an {@code INSERT ... ON CONFLICT DO NOTHING}. If another transaction inserted the
 * same (user, key) and has not committed yet, PostgreSQL makes this insert <em>wait</em> on the
 * unique index. When the first transaction commits, the insert returns 0 rows and we read its stored
 * response; if it rolled back, our insert succeeds and we run the operation. No "in progress"
 * state, no stale locks after a crash.
 */
@Component
class JdbcIdempotencyStore implements IdempotencyStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration ttl;

    JdbcIdempotencyStore(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${tesoro.idempotency.ttl:24h}") Duration ttl) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public <T> Optional<T> claim(UUID userId, String key, String requestFingerprint, Class<T> responseType) {
        Instant now = clock.instant();
        int inserted = jdbc.update(
                """
                INSERT INTO idempotency_keys (user_id, idem_key, request_hash, created_at, expires_at)
                VALUES (:userId, :key, :hash, :now, :expiresAt)
                ON CONFLICT (user_id, idem_key) DO NOTHING
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("key", key)
                        .addValue("hash", requestFingerprint)
                        .addValue("now", Timestamp.from(now))
                        .addValue("expiresAt", Timestamp.from(now.plus(ttl))));
        if (inserted == 1) {
            return Optional.empty();
        }

        Map<String, Object> existing = jdbc.queryForMap(
                "SELECT request_hash, response_body::text AS response FROM idempotency_keys"
                        + " WHERE user_id = :userId AND idem_key = :key",
                Map.of("userId", userId, "key", key));
        if (!requestFingerprint.equals(existing.get("request_hash"))) {
            throw new IdempotencyKeyReusedException(
                    "La clave de idempotencia ya se usó con otro contenido. Generá una clave nueva por operación");
        }
        String response = (String) existing.get("response");
        if (response == null) {
            throw new IllegalStateException("Idempotency key committed without a response: " + key);
        }
        return Optional.of(read(response, responseType));
    }

    @Override
    public void complete(UUID userId, String key, Object response) {
        jdbc.update(
                "UPDATE idempotency_keys SET response_body = CAST(:response AS jsonb)"
                        + " WHERE user_id = :userId AND idem_key = :key",
                Map.of("userId", userId, "key", key, "response", write(response)));
    }

    @Override
    public int deleteExpired(Instant now) {
        return jdbc.update("DELETE FROM idempotency_keys WHERE expires_at < :now", Map.of("now", Timestamp.from(now)));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize idempotent response", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read idempotent response", e);
        }
    }
}
