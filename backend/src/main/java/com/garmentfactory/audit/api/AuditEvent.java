package com.garmentfactory.audit.api;

/**
 * One sensitive action. {@code details} must never contain passwords, tokens or other secrets.
 * When {@code actorUserId} is null the recorder uses the currently authenticated user, if any.
 */
public record AuditEvent(Long actorUserId, String action, String entityType, String entityId, String details) {

    public static AuditEvent of(String action, String entityType, String entityId, String details) {
        return new AuditEvent(null, action, entityType, entityId, details);
    }

    public static AuditEvent forActor(Long actorUserId, String action, String entityType, String entityId, String details) {
        return new AuditEvent(actorUserId, action, entityType, entityId, details);
    }
}