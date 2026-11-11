package com.tesoro.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesoro.adapter.out.persistence.entity.OutboxEventEntity;
import com.tesoro.adapter.out.persistence.mapper.PersistenceMapper;
import com.tesoro.adapter.out.persistence.repository.OutboxEventJpaRepository;
import com.tesoro.application.model.OutboxMessage;
import com.tesoro.application.port.out.OutboxRepository;
import com.tesoro.domain.event.DomainEvent;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class OutboxPersistenceAdapter implements OutboxRepository {

    private final OutboxEventJpaRepository repository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PersistenceMapper mapper;
    private final EntityManager entityManager;

    OutboxPersistenceAdapter(
            OutboxEventJpaRepository repository,
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            PersistenceMapper mapper,
            EntityManager entityManager) {
        this.entityManager = entityManager;
        this.repository = repository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @Override
    public void enqueue(DomainEvent event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateType(event.aggregateType());
        entity.setAggregateId(event.aggregateId());
        entity.setEventType(event.eventType());
        entity.setPayload(toJson(event));
        entity.setCreatedAt(event.occurredAt());
        entity.setAttempts(0);
        entityManager.persist(entity);
    }

    @Override
    public List<OutboxMessage> lockNextBatch(int limit, int maxAttempts) {
        return jdbc.query(
                """
                SELECT id, aggregate_type, aggregate_id, event_type, payload::text AS payload,
                       created_at, published_at, attempts, last_error
                FROM outbox_events
                WHERE published_at IS NULL AND attempts < :maxAttempts
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """,
                Map.of("limit", limit, "maxAttempts", maxAttempts),
                (rs, i) -> new OutboxMessage(
                        rs.getObject("id", UUID.class),
                        rs.getString("aggregate_type"),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getTimestamp("created_at").toInstant(),
                        null,
                        rs.getInt("attempts"),
                        rs.getString("last_error")));
    }

    @Override
    public void markPublished(UUID id, Instant now) {
        jdbc.update(
                "UPDATE outbox_events SET published_at = :now, attempts = attempts + 1, last_error = NULL WHERE id = :id",
                Map.of("id", id, "now", Timestamp.from(now)));
    }

    @Override
    public void markFailed(UUID id, String error) {
        String truncated = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        jdbc.update(
                "UPDATE outbox_events SET attempts = attempts + 1, last_error = :error WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("error", truncated));
    }

    @Override
    public long countPending() {
        return repository.countByPublishedAtIsNull();
    }

    @Override
    public Page<OutboxMessage> findRecent(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDomain);
    }

    private String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + event.eventType(), e);
        }
    }
}
