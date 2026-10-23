package com.garmentfactory.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_audits")
public class InventoryAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @Column(name = "audit_number", nullable = false, updatable = false, length = 30)
    private String auditNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuditStatus status = AuditStatus.IN_PROGRESS;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "started_by_user_id", nullable = false, updatable = false)
    private Long startedByUserId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryAudit() { }

    public InventoryAudit(String auditNumber, Long startedByUserId, String notes) {
        this.auditNumber = auditNumber;
        this.startedByUserId = startedByUserId;
        this.notes = notes;
        this.startedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (publicId == null) publicId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void complete() { finish(AuditStatus.COMPLETED); }
    public void cancel()   { finish(AuditStatus.CANCELLED); }

    private void finish(AuditStatus target) {
        if (status != AuditStatus.IN_PROGRESS) {
            throw new IllegalStateException("Audit " + auditNumber + " is already " + status);
        }
        status = target;
        completedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getAuditNumber() { return auditNumber; }
    public AuditStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public Long getStartedByUserId() { return startedByUserId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
