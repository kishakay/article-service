package com.kishakay.articles.article.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL-backed idempotency store. Claims keys with {@code INSERT ... ON CONFLICT DO NOTHING}
 * so concurrent same-key requests coordinate in the database without catching unique violations
 * inside a failed transaction.
 */
@Repository
public class IdempotencyRecordRepository {

    private static final RowMapper<IdempotencyRecord> ROW_MAPPER = (rs, rowNum) -> new IdempotencyRecord(
            rs.getObject("id", UUID.class),
            rs.getString("operation"),
            rs.getString("idempotency_key"),
            rs.getString("request_fingerprint"),
            (Integer) rs.getObject("response_status"),
            rs.getString("response_body"),
            rs.getString("response_location"),
            rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Attempts to claim {@code (operation, key)}. Returns {@code true} when this transaction
     * inserted the row. Returns {@code false} when the key was already claimed (caller should
     * load and replay / conflict). Concurrent inserters block on the unique index until the
     * winner commits or rolls back.
     */
    public boolean tryClaim(String operation, String idempotencyKey, String requestFingerprint) {
        int inserted = jdbcTemplate.update(
                """
                        INSERT INTO idempotency_records
                            (id, operation, idempotency_key, request_fingerprint, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (operation, idempotency_key) DO NOTHING
                        """,
                UUID.randomUUID(),
                operation,
                idempotencyKey,
                requestFingerprint,
                Timestamp.from(Instant.now())
        );
        return inserted == 1;
    }

    public void complete(
            String operation,
            String idempotencyKey,
            int responseStatus,
            String responseBody,
            String responseLocation
    ) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE idempotency_records
                        SET response_status = ?, response_body = ?, response_location = ?
                        WHERE operation = ? AND idempotency_key = ?
                        """,
                responseStatus,
                responseBody,
                responseLocation,
                operation,
                idempotencyKey
        );
        if (updated != 1) {
            throw new IllegalStateException(
                    "Failed to complete idempotency record for operation=" + operation
                            + " key=" + idempotencyKey
            );
        }
    }

    public Optional<IdempotencyRecord> findByOperationAndKey(String operation, String idempotencyKey) {
        return jdbcTemplate.query(
                """
                        SELECT id, operation, idempotency_key, request_fingerprint,
                               response_status, response_body, response_location, created_at
                        FROM idempotency_records
                        WHERE operation = ? AND idempotency_key = ?
                        """,
                ROW_MAPPER,
                operation,
                idempotencyKey
        ).stream().findFirst();
    }
}
