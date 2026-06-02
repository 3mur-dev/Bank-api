package com.omar.bankapi.event;

public record AuditEventMessage(
        String action,
        String targetType,
        String targetId,
        boolean success,
        String reason,
        Object beforeData,
        Object afterData,
        String actorUsername
) {
}
