package com.garmentfactory.inventory.controller;

import static com.garmentfactory.inventory.dto.AuditDtos.*;

import com.garmentfactory.inventory.config.InventoryActorResolver;
import com.garmentfactory.inventory.dto.InventoryDtos.PageResponse;
import com.garmentfactory.inventory.entity.AuditStatus;
import com.garmentfactory.inventory.entity.InventoryAudit;
import com.garmentfactory.inventory.entity.InventoryAuditLine;
import com.garmentfactory.inventory.service.AuditLineLabelService;
import com.garmentfactory.inventory.service.AuditLineLabelService.ItemLabel;
import com.garmentfactory.inventory.service.InventoryAuditService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/audits")
public class InventoryAuditController {

    private final InventoryAuditService auditService;
    private final AuditLineLabelService labelService;
    private final InventoryActorResolver actor;

    public InventoryAuditController(InventoryAuditService auditService, AuditLineLabelService labelService,
                                    InventoryActorResolver actor) {
        this.auditService = auditService;
        this.labelService = labelService;
        this.actor = actor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_AUDIT')")
    public AuditResponse start(@Valid @RequestBody(required = false) StartAuditRequest req) {
        String notes = req == null ? null : req.notes();
        return AuditResponse.from(auditService.start(notes, actor.currentUserId()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_AUDIT')")
    public PageResponse<AuditResponse> list(@RequestParam(required = false) AuditStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(auditService.list(status, PageResponse.pageable(page, size)), AuditResponse::from);
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('INVENTORY_AUDIT')")
    public AuditDetailResponse detail(@PathVariable String publicId) {
        InventoryAudit audit = auditService.get(publicId);
        List<AuditLineResponse> lines = toResponses(auditService.lines(audit.getId()));
        int withVariance = (int) lines.stream().filter(line -> line.variance().signum() != 0).count();
        return new AuditDetailResponse(AuditResponse.from(audit), lines, withVariance);
    }

    @PostMapping("/{publicId}/counts/finished-goods")
    @PreAuthorize("hasAuthority('INVENTORY_AUDIT')")
    public AuditLineResponse countFinishedGood(@PathVariable String publicId,
                                               @Valid @RequestBody CountFinishedGoodRequest req) {
        InventoryAuditLine line = auditService.recordFinishedGoodCount(publicId, req.variantId(), req.countedQuantity());
        return toResponses(List.of(line)).get(0);
    }

    @PostMapping("/{publicId}/counts/raw-materials")
    @PreAuthorize("hasAuthority('INVENTORY_AUDIT')")
    public AuditLineResponse countRawMaterial(@PathVariable String publicId,
                                              @Valid @RequestBody CountRawMaterialRequest req) {
        InventoryAuditLine line = auditService.recordRawMaterialCount(publicId, req.rawMaterialId(), req.countedQuantity());
        return toResponses(List.of(line)).get(0);
    }

    @PostMapping("/{publicId}/complete")
    @PreAuthorize("hasAuthority('INVENTORY_AUDIT')")
    public AuditCompletionResponse complete(@PathVariable String publicId,
                                            @Valid @RequestBody CompleteAuditRequest req) {
        var result = auditService.complete(publicId, req.applyAdjustments(), actor.currentUserId());
        return new AuditCompletionResponse(AuditResponse.from(result.audit()), result.adjustmentsApplied());
    }

    @PostMapping("/{publicId}/cancel")
    @PreAuthorize("hasAuthority('INVENTORY_AUDIT')")
    public AuditResponse cancel(@PathVariable String publicId) {
        return AuditResponse.from(auditService.cancel(publicId));
    }

    private List<AuditLineResponse> toResponses(List<InventoryAuditLine> lines) {
        Map<Long, ItemLabel> labels = labelService.labelsByLineId(lines);
        return lines.stream().map(line -> {
            ItemLabel label = labels.get(line.getId());
            return AuditLineResponse.from(line, label.name(), label.sku());
        }).toList();
    }
}