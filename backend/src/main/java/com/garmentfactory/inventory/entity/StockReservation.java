package com.garmentfactory.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private Long variantId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockReservation() { }

    public StockReservation(Long orderId, Long variantId, int quantity, Instant expiresAt) {
        this.orderId = orderId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
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

    public boolean isActive() { return status == ReservationStatus.ACTIVE; }

    public void markConsumed() { transition(ReservationStatus.CONSUMED); consumedAt = Instant.now(); }
    public void markReleased() { transition(ReservationStatus.RELEASED); releasedAt = Instant.now(); }
    public void markExpired()  { transition(ReservationStatus.EXPIRED);  releasedAt = Instant.now(); }

    private void transition(ReservationStatus target) {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation " + publicId + " is " + status + ", cannot become " + target);
        }
        status = target;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getOrderId() { return orderId; }
    public Long getVariantId() { return variantId; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
