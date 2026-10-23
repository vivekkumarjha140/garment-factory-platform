package com.garmentfactory.inventory.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable ledger row. Insert-only (Hibernate @Immutable + DB triggers in V028). */
@Entity
@Immutable
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 36)
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, updatable = false, length = 20)
    private StockItemType itemType;

    @Column(name = "variant_id", updatable = false)
    private Long variantId;

    @Column(name = "raw_material_id", updatable = false)
    private Long rawMaterialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 20)
    private MovementType movementType;

    @Column(name = "on_hand_delta", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal onHandDelta;

    @Column(name = "reserved_delta", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal reservedDelta;

    @Column(name = "damaged_delta", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal damagedDelta;

    @Column(name = "balance_on_hand_after", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal balanceOnHandAfter;

    @Column(name = "reference_type", updatable = false, length = 40)
    private String referenceType;

    @Column(name = "reference_id", updatable = false, length = 64)
    private String referenceId;

    @Column(name = "reason", updatable = false, length = 500)
    private String reason;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() { }

    private StockMovement(StockItemType itemType, Long variantId, Long rawMaterialId,
                          MovementType movementType, BigDecimal onHandDelta, BigDecimal reservedDelta,
                          BigDecimal damagedDelta, BigDecimal balanceOnHandAfter,
                          String referenceType, String referenceId, String reason, Long actorUserId) {
        this.itemType = itemType;
        this.variantId = variantId;
        this.rawMaterialId = rawMaterialId;
        this.movementType = movementType;
        this.onHandDelta = onHandDelta;
        this.reservedDelta = reservedDelta;
        this.damagedDelta = damagedDelta;
        this.balanceOnHandAfter = balanceOnHandAfter;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reason = reason;
        this.actorUserId = actorUserId;
    }

    public static StockMovement forFinishedGood(Long variantId, MovementType type,
                                                int onHandDelta, int reservedDelta, int damagedDelta,
                                                int balanceOnHandAfter, String referenceType,
                                                String referenceId, String reason, Long actorUserId) {
        return new StockMovement(StockItemType.FINISHED_GOOD, variantId, null, type,
                BigDecimal.valueOf(onHandDelta), BigDecimal.valueOf(reservedDelta),
                BigDecimal.valueOf(damagedDelta), BigDecimal.valueOf(balanceOnHandAfter),
                referenceType, referenceId, reason, actorUserId);
    }

    public static StockMovement forRawMaterial(Long rawMaterialId, MovementType type,
                                               BigDecimal onHandDelta, BigDecimal reservedDelta,
                                               BigDecimal damagedDelta, BigDecimal balanceOnHandAfter,
                                               String referenceType, String referenceId,
                                               String reason, Long actorUserId) {
        return new StockMovement(StockItemType.RAW_MATERIAL, null, rawMaterialId, type,
                onHandDelta, reservedDelta, damagedDelta, balanceOnHandAfter,
                referenceType, referenceId, reason, actorUserId);
    }

    @PrePersist
    void onCreate() {
        if (publicId == null) publicId = UUID.randomUUID().toString();
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public StockItemType getItemType() { return itemType; }
    public Long getVariantId() { return variantId; }
    public Long getRawMaterialId() { return rawMaterialId; }
    public MovementType getMovementType() { return movementType; }
    public BigDecimal getOnHandDelta() { return onHandDelta; }
    public BigDecimal getReservedDelta() { return reservedDelta; }
    public BigDecimal getDamagedDelta() { return damagedDelta; }
    public BigDecimal getBalanceOnHandAfter() { return balanceOnHandAfter; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public String getReason() { return reason; }
    public Long getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
