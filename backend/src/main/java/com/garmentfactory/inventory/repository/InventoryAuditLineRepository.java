
package com.garmentfactory.inventory.repository;

import com.garmentfactory.inventory.entity.InventoryAuditLine;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAuditLineRepository
        extends JpaRepository<InventoryAuditLine, Long> {

    List<InventoryAuditLine> findByAuditId(Long auditId);

    List<InventoryAuditLine> findByAuditIdOrderByIdAsc(Long auditId);

    Optional<InventoryAuditLine> findByAuditIdAndVariantId(
            Long auditId,
            Long variantId);

    Optional<InventoryAuditLine> findByAuditIdAndRawMaterialId(
            Long auditId,
            Long rawMaterialId);
}
