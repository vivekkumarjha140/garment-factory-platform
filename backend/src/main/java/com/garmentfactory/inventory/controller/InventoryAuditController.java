
package com.garmentfactory.inventory.controller;

import com.garmentfactory.inventory.config.InventoryActorResolver;
import com.garmentfactory.inventory.entity.AuditStatus;
import com.garmentfactory.inventory.entity.InventoryAudit;
import com.garmentfactory.inventory.entity.InventoryAuditLine;
import com.garmentfactory.inventory.dto.AuditDtos.AuditCompletionResponse;
import com.garmentfactory.inventory.dto.AuditDtos.AuditDetailResponse;
import com.garmentfactory.inventory.dto.AuditDtos.AuditLineResponse;
import com.garmentfactory.inventory.dto.AuditDtos.AuditResponse;
import com.garmentfactory.inventory.dto.AuditDtos.CompleteAuditRequest;
import com.garmentfactory.inventory.dto.AuditDtos.CountFinishedGoodRequest;
import com.garmentfactory.inventory.dto.AuditDtos.CountRawMaterialRequest;
import com.garmentfactory.inventory.dto.AuditDtos.StartAuditRequest;
import com.garmentfactory.inventory.service.InventoryAuditService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/audits")
@Validated
@PreAuthorize("hasAuthority('INVENTORY_AUDIT')")
public class InventoryAuditController {

    private final InventoryAuditService auditService;
    private final InventoryActorResolver actorResolver;

    public InventoryAuditController(
            InventoryAuditService auditService,
            InventoryActorResolver actorResolver) {

        this.auditService = auditService;
        this.actorResolver = actorResolver;
    }

    // ------------------------------------------------------------
    // Start audit
    // ------------------------------------------------------------

    @PostMapping
    public ResponseEntity<AuditResponse> start(
            @Valid @RequestBody StartAuditRequest request) {

        InventoryAudit audit = auditService.start(
                request.notes(),
                actorResolver.currentUserId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AuditResponse.from(audit));
    }

    // ------------------------------------------------------------
    // List audits
    // ------------------------------------------------------------

    @GetMapping
    public Page<AuditResponse> list(
            @RequestParam(required = false) AuditStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return auditService.list(status, pageable)
                .map(AuditResponse::from);
    }

    // ------------------------------------------------------------
    // Get audit details
    // ------------------------------------------------------------

    @GetMapping("/{publicId}")
    public AuditDetailResponse get(
            @PathVariable String publicId) {

        InventoryAudit audit = auditService.get(publicId);

        List<AuditLineResponse> lines = auditService
                .lines(audit.getId())
                .stream()
                .map(AuditLineResponse::from)
                .toList();

        List<AuditLineResponse> linesWithVariance = lines
                .stream()
                .filter(line -> line.variance().signum() != 0)
                .toList();

        return new AuditDetailResponse(
                AuditResponse.from(audit),
                lines,
                linesWithVariance);
    }

    // ------------------------------------------------------------
    // Count finished goods
    // ------------------------------------------------------------

    @PostMapping("/{publicId}/counts/finished-goods")
    public ResponseEntity<AuditLineResponse> recordFinishedGoodCount(
            @PathVariable String publicId,
            @Valid @RequestBody CountFinishedGoodRequest request) {

        InventoryAuditLine line = auditService.recordFinishedGoodCount(
                publicId,
                request.variantId(),
                request.countedQuantity());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AuditLineResponse.from(line));
    }

    // ------------------------------------------------------------
    // Count raw materials
    // ------------------------------------------------------------

    @PostMapping("/{publicId}/counts/raw-materials")
    public ResponseEntity<AuditLineResponse> recordRawMaterialCount(
            @PathVariable String publicId,
            @Valid @RequestBody CountRawMaterialRequest request) {

        InventoryAuditLine line = auditService.recordRawMaterialCount(
                publicId,
                request.rawMaterialId(),
                request.countedQuantity());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AuditLineResponse.from(line));
    }

    // ------------------------------------------------------------
    // Complete audit
    // ------------------------------------------------------------

    @PostMapping("/{publicId}/complete")
    public AuditCompletionResponse complete(
            @PathVariable String publicId,
            @Valid @RequestBody CompleteAuditRequest request) {

        InventoryAuditService.AuditCompletion completion =
                auditService.complete(
                        publicId,
                        request.applyAdjustments(),
                        actorResolver.currentUserId());

        return new AuditCompletionResponse(
                AuditResponse.from(completion.audit()),
                completion.adjustmentsApplied());
    }

    // ------------------------------------------------------------
    // Cancel audit
    // ------------------------------------------------------------

    @PostMapping("/{publicId}/cancel")
    public AuditResponse cancel(
            @PathVariable String publicId) {

        InventoryAudit audit = auditService.cancel(publicId);

        return AuditResponse.from(audit);
    }
}
