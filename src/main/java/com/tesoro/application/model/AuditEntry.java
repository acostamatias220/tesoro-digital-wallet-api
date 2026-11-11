package com.tesoro.application.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Who did what and when. The persistence adapter enriches it with the caller IP when available. */
public record AuditEntry(
        Long id,
        Instant occurredAt,
        UUID actorId,
        String action,
        String targetType,
        String targetId,
        String ipAddress,
        Map<String, Object> details) {

    public static AuditEntry of(
            Instant now, UUID actorId, String action, String targetType, Object targetId, Map<String, Object> details) {
        return new AuditEntry(
                null, now, actorId, action, targetType, targetId == null ? null : targetId.toString(), null, details);
    }
}
