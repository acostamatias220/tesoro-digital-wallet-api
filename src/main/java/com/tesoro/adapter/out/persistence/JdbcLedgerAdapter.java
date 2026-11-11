package com.tesoro.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesoro.application.model.AccountMovement;
import com.tesoro.application.model.MovementFilter;
import com.tesoro.application.model.SystemAccountBalance;
import com.tesoro.application.model.TransactionDetails;
import com.tesoro.application.port.out.LedgerRepository;
import com.tesoro.domain.ledger.EntryType;
import com.tesoro.domain.ledger.JournalEntry;
import com.tesoro.domain.model.AccountKind;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Direction;
import com.tesoro.domain.model.Money;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The ledger is append-only and read through reporting queries, so it is accessed with plain SQL
 * instead of JPA: a batch insert for postings and hand-written queries for statements.
 */
@Component
class JdbcLedgerAdapter implements LedgerRepository {

    private static final TypeReference<Map<String, String>> METADATA = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcLedgerAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(JournalEntry entry, Map<UUID, Money> balancesAfter) {
        Timestamp createdAt = Timestamp.from(entry.createdAt());
        jdbc.update(
                """
                INSERT INTO journal_entries (id, type, description, initiated_by, metadata, created_at)
                VALUES (:id, :type, :description, :initiatedBy, CAST(:metadata AS jsonb), :createdAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", entry.id())
                        .addValue("type", entry.type().name())
                        .addValue("description", entry.description())
                        .addValue("initiatedBy", entry.initiatedBy())
                        .addValue("metadata", toJson(entry.metadata()))
                        .addValue("createdAt", createdAt));

        MapSqlParameterSource[] rows = entry.postings().stream()
                .map(posting -> new MapSqlParameterSource()
                        .addValue("entryId", entry.id())
                        .addValue("accountId", posting.accountId())
                        .addValue("currency", posting.amount().currency().name())
                        .addValue("direction", posting.direction().name())
                        .addValue("amount", posting.amount().amount())
                        .addValue(
                                "balanceAfter",
                                Optional.ofNullable(balancesAfter.get(posting.accountId()))
                                        .map(Money::amount)
                                        .orElse(null))
                        .addValue("createdAt", createdAt))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO postings (journal_entry_id, account_id, currency, direction, amount, balance_after, created_at)
                VALUES (:entryId, :accountId, :currency, :direction, :amount, :balanceAfter, :createdAt)
                """, rows);
    }

    @Override
    public Optional<TransactionDetails> findDetails(UUID entryId) {
        List<TransactionDetails.Line> lines = jdbc.query(
                """
                SELECT p.account_id, p.currency, p.direction, p.amount, a.code, u.full_name
                FROM postings p
                JOIN accounts a ON a.id = p.account_id
                LEFT JOIN users u ON u.id = a.owner_id
                WHERE p.journal_entry_id = :id
                ORDER BY p.id
                """,
                Map.of("id", entryId),
                (rs, i) -> new TransactionDetails.Line(
                        rs.getObject("account_id", UUID.class),
                        rs.getString("code") != null ? rs.getString("code") : rs.getString("full_name"),
                        CurrencyCode.valueOf(rs.getString("currency")),
                        Direction.valueOf(rs.getString("direction")),
                        rs.getBigDecimal("amount")));
        return jdbc
                .query(
                        "SELECT id, type, description, initiated_by, metadata::text AS metadata, created_at"
                                + " FROM journal_entries WHERE id = :id",
                        Map.of("id", entryId),
                        (rs, i) -> new TransactionDetails(
                                rs.getObject("id", UUID.class),
                                EntryType.valueOf(rs.getString("type")),
                                rs.getString("description"),
                                rs.getObject("initiated_by", UUID.class),
                                fromJson(rs.getString("metadata")),
                                lines,
                                rs.getTimestamp("created_at").toInstant()))
                .stream()
                .findFirst();
    }

    @Override
    public boolean involvesUser(UUID entryId, UUID userId) {
        Boolean involved = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM postings p JOIN accounts a ON a.id = p.account_id
                               WHERE p.journal_entry_id = :entryId AND a.owner_id = :userId)
                """, Map.of("entryId", entryId, "userId", userId), Boolean.class);
        return Boolean.TRUE.equals(involved);
    }

    @Override
    public Page<AccountMovement> findMovements(UUID accountId, MovementFilter filter, Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE p.account_id = :accountId");
        MapSqlParameterSource params = new MapSqlParameterSource("accountId", accountId);
        if (filter.type() != null) {
            where.append(" AND e.type = :type");
            params.addValue("type", filter.type().name());
        }
        if (filter.direction() != null) {
            where.append(" AND p.direction = :direction");
            params.addValue("direction", filter.direction().name());
        }
        if (filter.from() != null) {
            where.append(" AND p.created_at >= :from");
            params.addValue("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" AND p.created_at < :to");
            params.addValue("to", Timestamp.from(filter.to()));
        }
        String from = " FROM postings p JOIN journal_entries e ON e.id = p.journal_entry_id";

        Long total = jdbc.queryForObject("SELECT count(*)" + from + where, params, Long.class);
        params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        List<AccountMovement> content = jdbc.query(
                "SELECT p.id, p.journal_entry_id, e.type, p.direction, p.amount, p.currency, p.balance_after,"
                        + " e.description, p.created_at"
                        + from
                        + where
                        + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset",
                params,
                (rs, i) -> new AccountMovement(
                        rs.getLong("id"),
                        rs.getObject("journal_entry_id", UUID.class),
                        EntryType.valueOf(rs.getString("type")),
                        Direction.valueOf(rs.getString("direction")),
                        rs.getBigDecimal("amount"),
                        CurrencyCode.valueOf(rs.getString("currency")),
                        rs.getBigDecimal("balance_after"),
                        rs.getString("description"),
                        rs.getTimestamp("created_at").toInstant()));
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<SystemAccountBalance> systemBalances() {
        return jdbc.query("""
                SELECT a.id, a.code, a.kind, a.currency,
                       COALESCE(SUM(CASE WHEN p.direction = 'DEBIT' THEN p.amount ELSE -p.amount END), 0) AS net_debit
                FROM accounts a
                LEFT JOIN postings p ON p.account_id = a.id
                WHERE a.kind <> 'USER_WALLET'
                GROUP BY a.id, a.code, a.kind, a.currency
                ORDER BY a.code
                """, (rs, i) -> {
            AccountKind kind = AccountKind.valueOf(rs.getString("kind"));
            var netDebit = rs.getBigDecimal("net_debit");
            var balance = kind.normalSide() == Direction.DEBIT ? netDebit : netDebit.negate();
            return new SystemAccountBalance(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
                    kind,
                    CurrencyCode.valueOf(rs.getString("currency")),
                    balance);
        });
    }

    private String toJson(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize entry metadata", e);
        }
    }

    private Map<String, String> fromJson(String json) {
        try {
            return objectMapper.readValue(json, METADATA);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read entry metadata", e);
        }
    }
}
