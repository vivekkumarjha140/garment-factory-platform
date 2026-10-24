
package com.garmentfactory.inventory.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.inventory.entity.AuditStatus;
import com.garmentfactory.inventory.entity.FinishedGoodsStock;
import com.garmentfactory.inventory.entity.InventoryAudit;
import com.garmentfactory.inventory.entity.InventoryAuditLine;
import com.garmentfactory.inventory.entity.RawMaterial;
import com.garmentfactory.inventory.entity.StockItemType;
import com.garmentfactory.inventory.entity.StockMovement;
import com.garmentfactory.inventory.exception.InvalidStockOperationException;
import com.garmentfactory.inventory.exception.StockConflictException;
import com.garmentfactory.inventory.exception.StockNotFoundException;
import com.garmentfactory.inventory.repository.FinishedGoodsStockRepository;
import com.garmentfactory.inventory.repository.InventoryAuditLineRepository;
import com.garmentfactory.inventory.repository.InventoryAuditRepository;
import com.garmentfactory.inventory.repository.RawMaterialRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Physical stock-count workflow: start, record counts, then complete
 * (optionally applying variances).
 */
@Service
public class InventoryAuditService {

    private static final int QTY_SCALE = 3;
    private static final String ENTITY = "INVENTORY_AUDIT";

    public record AuditCompletion(InventoryAudit audit, int adjustmentsApplied) {
    }

    private final InventoryAuditRepository auditRepository;
    private final InventoryAuditLineRepository lineRepository;
    private final FinishedGoodsStockRepository stockRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final FinishedGoodsStockService stockService;
    private final RawMaterialStockService rawMaterialService;
    private final AuditRecorder auditRecorder;

    public InventoryAuditService(
            InventoryAuditRepository auditRepository,
            InventoryAuditLineRepository lineRepository,
            FinishedGoodsStockRepository stockRepository,
            RawMaterialRepository rawMaterialRepository,
            FinishedGoodsStockService stockService,
            RawMaterialStockService rawMaterialService,
            AuditRecorder auditRecorder) {

        this.auditRepository = auditRepository;
        this.lineRepository = lineRepository;
        this.stockRepository = stockRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.stockService = stockService;
        this.rawMaterialService = rawMaterialService;
        this.auditRecorder = auditRecorder;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public InventoryAudit get(String publicId) {
        return load(publicId);
    }

    @Transactional(readOnly = true)
    public List<InventoryAuditLine> lines(Long auditId) {
        return lineRepository.findByAuditIdOrderByIdAsc(auditId);
    }

    @Transactional(readOnly = true)
    public Page<InventoryAudit> list(AuditStatus status, Pageable pageable) {
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id"));

        return status == null
                ? auditRepository.findAll(sorted)
                : auditRepository.findByStatus(status, sorted);
    }

    // ------------------------------------------------------------------
    // Workflow
    // ------------------------------------------------------------------

    @Transactional
    public InventoryAudit start(String notes, Long actorUserId) {
        String cleaned = notes == null || notes.isBlank()
                ? null
                : notes.trim();

        InventoryAudit saved = auditRepository.save(
                new InventoryAudit(
                        AuditNumbers.next(),
                        actorUserId,
                        cleaned));

        auditRecorder.record(
                AuditEvent.of(
                        "INVENTORY_AUDIT_STARTED",
                        ENTITY,
                        saved.getAuditNumber(),
                        cleaned == null
                                ? "no notes"
                                : "notes=" + AuditText.shorten(cleaned, 500)));

        return saved;
    }

    /**
     * Records or re-records the physical count for one finished-goods variant.
     */
    @Transactional
    public InventoryAuditLine recordFinishedGoodCount(
            String auditPublicId,
            Long variantId,
            int countedQuantity) {

        InventoryAudit audit = loadInProgress(auditPublicId);

        if (countedQuantity < 0) {
            throw new InvalidStockOperationException(
                    "Counted quantity cannot be negative");
        }

        FinishedGoodsStock stock = stockRepository.findByVariantId(variantId)
                .orElseThrow(() -> new StockNotFoundException(
                        "No stock record for variant " + variantId));

        lineRepository.findByAuditIdAndVariantId(audit.getId(), variantId)
                .ifPresent(lineRepository::delete);

        return lineRepository.save(
                new InventoryAuditLine(
                        audit.getId(),
                        StockItemType.FINISHED_GOOD,
                        variantId,
                        null,
                        BigDecimal.valueOf(stock.getOnHand()),
                        BigDecimal.valueOf(countedQuantity)));
    }

    /**
     * Records or re-records the physical count for one raw material.
     */
    @Transactional
    public InventoryAuditLine recordRawMaterialCount(
            String auditPublicId,
            Long rawMaterialId,
            BigDecimal countedQuantity) {

        InventoryAudit audit = loadInProgress(auditPublicId);

        if (countedQuantity == null || countedQuantity.signum() < 0) {
            throw new InvalidStockOperationException(
                    "Counted quantity cannot be negative");
        }

        if (countedQuantity.stripTrailingZeros().scale() > QTY_SCALE) {
            throw new InvalidStockOperationException(
                    "Quantity supports at most "
                            + QTY_SCALE
                            + " decimal places");
        }

        RawMaterial material = rawMaterialRepository.findById(rawMaterialId)
                .orElseThrow(() -> new StockNotFoundException(
                        "Raw material not found: " + rawMaterialId));

        lineRepository.findByAuditIdAndRawMaterialId(
                        audit.getId(),
                        rawMaterialId)
                .ifPresent(lineRepository::delete);

        return lineRepository.save(
                new InventoryAuditLine(
                        audit.getId(),
                        StockItemType.RAW_MATERIAL,
                        null,
                        rawMaterialId,
                        material.getOnHand(),
                        countedQuantity));
    }

    /**
     * Closes the audit.
     *
     * With applyAdjustments=true, every non-zero variance becomes an
     * ADJUSTMENT ledger row. With false, the audit is closed without
     * changing stock.
     */
    @Transactional
    public AuditCompletion complete(
            String auditPublicId,
            boolean applyAdjustments,
            Long actorUserId) {

        InventoryAudit audit = loadInProgress(auditPublicId);

        List<InventoryAuditLine> lines = new ArrayList<>(
                lineRepository.findByAuditIdOrderByIdAsc(audit.getId()));

        if (lines.isEmpty()) {
            throw new InvalidStockOperationException(
                    "Audit has no counted items");
        }

        Set<String> seen = new HashSet<>();

        for (InventoryAuditLine line : lines) {
            if (!seen.add(itemKey(line))) {
                throw new StockConflictException(
                        "Audit contains duplicate count lines for "
                                + itemKey(line));
            }
        }

        int adjustments = 0;

        if (applyAdjustments) {
            lines.sort(
                    Comparator.comparing(InventoryAuditLine::getItemType)
                            .thenComparing(InventoryAuditService::itemId));

            String reason = "Inventory audit "
                    + audit.getAuditNumber()
                    + " variance";

            for (InventoryAuditLine line : lines) {
                if (line.getVariance().signum() == 0) {
                    continue;
                }

                StockMovement movement =
                        line.getItemType() == StockItemType.FINISHED_GOOD
                                ? stockService.adjustForAudit(
                                line.getVariantId(),
                                line.getVariance().intValueExact(),
                                reason,
                                audit.getPublicId(),
                                actorUserId)
                                : rawMaterialService.adjustForAudit(
                                line.getRawMaterialId(),
                                line.getVariance(),
                                reason,
                                audit.getPublicId(),
                                actorUserId);

                line.linkAdjustment(movement.getId());
                lineRepository.save(line);

                adjustments++;
            }
        }

        audit.complete();

        InventoryAudit saved = auditRepository.save(audit);

        auditRecorder.record(
                AuditEvent.of(
                        "INVENTORY_AUDIT_COMPLETED",
                        ENTITY,
                        audit.getAuditNumber(),
                        "lines=" + lines.size()
                                + ", applyAdjustments=" + applyAdjustments
                                + ", adjustments=" + adjustments));

        return new AuditCompletion(saved, adjustments);
    }

    @Transactional
    public InventoryAudit cancel(String auditPublicId) {
        InventoryAudit audit = loadInProgress(auditPublicId);

        audit.cancel();

        InventoryAudit saved = auditRepository.save(audit);

        auditRecorder.record(
                AuditEvent.of(
                        "INVENTORY_AUDIT_CANCELLED",
                        ENTITY,
                        audit.getAuditNumber(),
                        "no stock changed"));

        return saved;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private InventoryAudit load(String publicId) {
        return auditRepository.findByPublicId(publicId)
                .orElseThrow(() -> new StockNotFoundException(
                        "Inventory audit not found: " + publicId));
    }

    private InventoryAudit loadInProgress(String publicId) {
        InventoryAudit audit = load(publicId);

        if (audit.getStatus() != AuditStatus.IN_PROGRESS) {
            throw new StockConflictException(
                    "Audit "
                            + audit.getAuditNumber()
                            + " is already "
                            + audit.getStatus());
        }

        return audit;
    }

    private static long itemId(InventoryAuditLine line) {
        return line.getVariantId() != null
                ? line.getVariantId()
                : line.getRawMaterialId();
    }

    private static String itemKey(InventoryAuditLine line) {
        return line.getItemType() + ":" + itemId(line);
    }
}
