package com.garmentfactory.inventory.repository;

import com.garmentfactory.inventory.entity.RawMaterial;
import com.garmentfactory.inventory.entity.RawMaterialCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface RawMaterialRepository extends JpaRepository<RawMaterial, Long> {

    Optional<RawMaterial> findByCode(String code);

    Optional<RawMaterial> findByPublicId(String publicId);

    boolean existsByCode(String code);

    Page<RawMaterial> findByCategoryAndActiveTrue(RawMaterialCategory category, Pageable pageable);

    @Query("SELECT r FROM RawMaterial r WHERE r.active = true AND (r.onHand - r.reserved) <= r.lowStockThreshold")
    Page<RawMaterial> findLowStock(Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE raw_materials
               SET on_hand = on_hand + :qty, version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE id = :id
            """, nativeQuery = true)
    int receive(@Param("id") Long id, @Param("qty") BigDecimal qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE raw_materials
               SET reserved = reserved + :qty, version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE id = :id AND (on_hand - reserved) >= :qty
            """, nativeQuery = true)
    int tryReserve(@Param("id") Long id, @Param("qty") BigDecimal qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE raw_materials
               SET reserved = reserved - :qty, version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE id = :id AND reserved >= :qty
            """, nativeQuery = true)
    int tryRelease(@Param("id") Long id, @Param("qty") BigDecimal qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE raw_materials
               SET on_hand = on_hand - :qty, reserved = reserved - :qty,
                   version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE id = :id AND reserved >= :qty AND on_hand >= :qty
            """, nativeQuery = true)
    int tryDeductReserved(@Param("id") Long id, @Param("qty") BigDecimal qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE raw_materials
               SET on_hand = on_hand + :delta, version = version + 1, updated_at = UTC_TIMESTAMP(6)
             WHERE id = :id AND (on_hand + :delta) >= reserved
            """, nativeQuery = true)
    int tryAdjust(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
