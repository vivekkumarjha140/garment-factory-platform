package com.garmentfactory.shipping.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shipping_rates")
public class ShippingRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false, updatable = false)
    private Long zoneId;

    /** Slab is [minWeightGrams, maxWeightGrams) */
    @Column(name = "min_weight_grams", nullable = false)
    private int minWeightGrams;

    @Column(name = "max_weight_grams", nullable = false)
    private int maxWeightGrams;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal charge;

    @Column(name = "estimated_days_min", nullable = false)
    private short estimatedDaysMin = 3;

    @Column(name = "estimated_days_max", nullable = false)
    private short estimatedDaysMax = 7;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShippingRate() {
    }

    public ShippingRate(Long zoneId, int minWeightGrams, int maxWeightGrams, BigDecimal charge) {
        this.zoneId = zoneId;
        this.minWeightGrams = minWeightGrams;
        this.maxWeightGrams = maxWeightGrams;
        this.charge = charge;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getZoneId() { return zoneId; }
    public int getMinWeightGrams() { return minWeightGrams; }
    public void setMinWeightGrams(int v) { this.minWeightGrams = v; }
    public int getMaxWeightGrams() { return maxWeightGrams; }
    public void setMaxWeightGrams(int v) { this.maxWeightGrams = v; }
    public BigDecimal getCharge() { return charge; }
    public void setCharge(BigDecimal charge) { this.charge = charge; }
    public short getEstimatedDaysMin() { return estimatedDaysMin; }
    public void setEstimatedDaysMin(short v) { this.estimatedDaysMin = v; }
    public short getEstimatedDaysMax() { return estimatedDaysMax; }
    public void setEstimatedDaysMax(short v) { this.estimatedDaysMax = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}