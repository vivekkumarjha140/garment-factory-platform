package com.garmentfactory.audit.service;

import com.garmentfactory.audit.dto.AuditLogResponse;
import com.garmentfactory.audit.entity.AuditLog;
import com.garmentfactory.audit.repository.AuditLogRepository;
import com.garmentfactory.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String action, String entityType, String entityId, Pageable pageable) {
        String a = clean(action);
        String type = clean(entityType);
        String id = clean(entityId);
        Page<AuditLog> page = repository.search(a.isEmpty(), a, type.isEmpty(), type, id.isEmpty(), id, pageable);
        return PageResponse.from(page.map(AuditQueryService::toResponse));
    }

    private static AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getAction(), log.getEntityType(),
                log.getEntityId(), log.getDetails(), log.getIp(), log.getCreatedAt());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}