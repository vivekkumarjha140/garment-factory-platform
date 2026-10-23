package com.garmentfactory.inventory.repository;

import com.garmentfactory.inventory.entity.FinishedGoodsStock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FinishedGoodsStockRepository extends JpaRepository<FinishedGoodsStock, Long> {

    Optional<FinishedGoodsStock> findByVariantId(Long variantId);

    boolean existsByVariantId(Long variantId);

    List<FinishedGoodsStock> findByVariantIdIn(Collection<Long> variantIds);

    @Query("SELECT s FROM FinishedGoodsStock s WHERE (s.onHand - s.reserved) <= s.lowStockThreshold")
    Page<FinishedGoodsStock> findLowStock(Pageable pageable);

    // ---------- Atomic single-row mutations. Return 1 = applied, 0 = rejected. ----------

    /** Reserve: succeeds only if available (on_hand - reserved) >= qty. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE finished_goods_stock
               SET reserved = reserved + :qty, version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE variant_id = :variantId AND (on_hand - reserved) >= :qty
            """, nativeQuery = true)
    int tryReserve(@Param("variantId") Long variantId, @Param("qty") int qty);

    /** Release a reservation back to available. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE finished_goods_stock
               SET reserved = reserved - :qty, version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE variant_id = :variantId AND reserved >= :qty
            """, nativeQuery = true)
    int tryRelease(@Param("variantId") Long variantId, @Param("qty") int qty);

    /** Deduct (consume a reservation): physical stock leaves, reservation is cleared. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE finished_goods_stock
               SET on_hand = on_hand - :qty, reserved = reserved - :qty,
                   version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE variant_id = :variantId AND reserved >= :qty AND on_hand >= :qty
            """, nativeQuery = true)
    int tryDeductReserved(@Param("variantId") Long variantId, @Param("qty") int qty);

    /** Goods receipt: increases on_hand. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE finished_goods_stock
               SET on_hand = on_hand + :qty, version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE variant_id = :variantId
            """, nativeQuery = true)
    int receive(@Param("variantId") Long variantId, @Param("qty") int qty);

    /** Manual adjustment (delta may be negative). Never lets on_hand drop below reserved. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE finished_goods_stock
               SET on_hand = on_hand + :delta, version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE variant_id = :variantId AND (on_hand + :delta) >= reserved
            """, nativeQuery = true)
    int tryAdjust(@Param("variantId") Long variantId, @Param("delta") int delta);

    /** Move sellable (unreserved) stock into the damaged bucket. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE finished_goods_stock
               SET on_hand = on_hand - :qty, damaged = damaged + :qty,
                   version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE variant_id = :variantId AND (on_hand - reserved) >= :qty
            """, nativeQuery = true)
    int tryMarkDamaged(@Param("variantId") Long variantId, @Param("qty") int qty);
}
