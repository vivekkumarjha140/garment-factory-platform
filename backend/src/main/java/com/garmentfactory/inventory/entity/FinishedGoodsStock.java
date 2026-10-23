package com.garmentfactory.inventory.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Finished-goods stock row (1:1 with a product variant).
 * Counters have NO setters on purpose: every change goes through the atomic
 * UPDATE queries in FinishedGoodsStockRepository + a stock_movements ledger row.
 */
@Entity
@Table(name = "finished_goods_stock")
public class FinishedGoodsStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private Long variantId;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(name = "reserved", nullable = false)
    private int reserved;

    @Column(name = "damaged", nullable = false)
    private int damaged;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FinishedGoodsStock() { }

    public FinishedGoodsStock(Long variantId, int lowStockThreshold) {
        this.variantId = variantId;
        this.lowStockThreshold = lowStockThreshold;
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

    public int getAvailable() { return onHand - reserved; }
    public boolean isLowStock() { return getAvailable() <= lowStockThreshold; }

    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getVariantId() { return variantId; }
    public int getOnHand() { return onHand; }
    public int getReserved() { return reserved; }
    public int getDamaged() { return damaged; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
