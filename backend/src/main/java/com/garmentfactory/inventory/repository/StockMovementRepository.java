package com.garmentfactory.inventory.repository;

import com.garmentfactory.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByVariantIdOrderByCreatedAtDescIdDesc(Long variantId, Pageable pageable);

    Page<StockMovement> findByRawMaterialIdOrderByCreatedAtDescIdDesc(Long rawMaterialId, Pageable pageable);

    Page<StockMovement> findByReferenceTypeAndReferenceIdOrderByCreatedAtDescIdDesc(
            String referenceType, String referenceId, Pageable pageable);
}
