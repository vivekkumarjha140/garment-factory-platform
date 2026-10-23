package com.garmentfactory.audit.dto;

import java.time.Instant;

public record AuditLogResponse(Long id, Long actorUserId, String action, String entityType, String entityId,
                               String details, String ip, Instant createdAt) {
}