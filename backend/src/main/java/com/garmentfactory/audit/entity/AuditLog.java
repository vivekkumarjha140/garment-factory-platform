package com.garmentfactory.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable once written: there are no setters and no update or delete code anywhere. */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(nullable = false, updatable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", updatable = false, length = 64)
    private String entityId;

    @Column(updatable = false, length = 1000)
    private String details;

    @Column(updatable = false, length = 45)
    private String ip;

    @Column(name = "user_agent", updatable = false, length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditLog(Long actorUserId, String action, String entityType, String entityId,
                    String details, String ip, String userAgent) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.ip = ip;
        this.userAgent = userAgent;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}