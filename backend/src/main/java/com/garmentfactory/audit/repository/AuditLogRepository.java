package com.garmentfactory.audit.repository;

import com.garmentfactory.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Each filter is switched off by its "any" flag, so no parameter is ever null. Newest first. */
    @Query(value = "select a from AuditLog a where (:anyAction = true or a.action = :action) "
            + "and (:anyEntityType = true or a.entityType = :entityType) "
            + "and (:anyEntityId = true or a.entityId = :entityId) "
            + "order by a.createdAt desc, a.id desc",
            countQuery = "select count(a) from AuditLog a where (:anyAction = true or a.action = :action) "
                    + "and (:anyEntityType = true or a.entityType = :entityType) "
                    + "and (:anyEntityId = true or a.entityId = :entityId)")
    Page<AuditLog> search(@Param("anyAction") boolean anyAction, @Param("action") String action,
                          @Param("anyEntityType") boolean anyEntityType, @Param("entityType") String entityType,
                          @Param("anyEntityId") boolean anyEntityId, @Param("entityId") String entityId,
                          Pageable pageable);
}