
package com.garmentfactory.inventory.repository;

import com.garmentfactory.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Insert-only on purpose: the ledger exposes no update or delete operation.
 */
public interface StockMovementRepository extends Repository<StockMovement, Long> {

    StockMovement save(StockMovement movement);

    Page<StockMovement> findByVariantIdOrderByCreatedAtDescIdDesc(
            Long variantId, Pageable pageable);

    Page<StockMovement> findByRawMaterialIdOrderByCreatedAtDescIdDesc(
            Long rawMaterialId, Pageable pageable);

    Page<StockMovement> findByReferenceTypeAndReferenceIdOrderByCreatedAtDescIdDesc(
            String referenceType, String referenceId, Pageable pageable);
}
