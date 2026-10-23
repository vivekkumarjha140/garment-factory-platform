package com.garmentfactory.audit.controller;

import com.garmentfactory.audit.dto.AuditLogResponse;
import com.garmentfactory.audit.service.AuditQueryService;
import com.garmentfactory.shared.web.PageResponse;
import com.garmentfactory.shared.web.Paging;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@Tag(name = "Admin: audit")
public class AuditLogController {

    private final AuditQueryService service;

    public AuditLogController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public PageResponse<AuditLogResponse> search(@RequestParam(required = false) String action,
                                                 @RequestParam(required = false) String entityType,
                                                 @RequestParam(required = false) String entityId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.search(action, entityType, entityId, Paging.of(page, size));
    }
}