package com.garmentfactory.inventory.repository;

import com.garmentfactory.inventory.entity.AuditStatus;
import com.garmentfactory.inventory.entity.InventoryAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryAuditRepository extends JpaRepository<InventoryAudit, Long> {

    Optional<InventoryAudit> findByPublicId(String publicId);

    Page<InventoryAudit> findByStatus(AuditStatus status, Pageable pageable);
}
