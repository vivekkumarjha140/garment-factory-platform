package com.garmentfactory.inventory.dto;

import com.garmentfactory.inventory.entity.AuditStatus;
import com.garmentfactory.inventory.entity.InventoryAudit;
import com.garmentfactory.inventory.entity.InventoryAuditLine;
import com.garmentfactory.inventory.entity.StockItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AuditDtos {

    private AuditDtos() { }

    public record StartAuditRequest(@Size(max = 500) String notes) { }

    public record CountFinishedGoodRequest(@NotNull Long variantId, @NotNull @Min(0) Integer countedQuantity) { }

    public record CountRawMaterialRequest(@NotNull Long rawMaterialId,
                                          @NotNull @DecimalMin("0.0") BigDecimal countedQuantity) { }

    /** applyAdjustments is mandatory on purpose: completing an audit may change stock. */
    public record CompleteAuditRequest(@NotNull Boolean applyAdjustments) { }

    public record AuditResponse(String publicId, String auditNumber, AuditStatus status, String notes,
                                Long startedByUserId, Instant startedAt, Instant completedAt) {
        public static AuditResponse from(InventoryAudit a) {
            return new AuditResponse(a.getPublicId(), a.getAuditNumber(), a.getStatus(), a.getNotes(),
                    a.getStartedByUserId(), a.getStartedAt(), a.getCompletedAt());
        }
    }

    /** itemName and sku come from the catalog (variants) or the raw material master. */
    public record AuditLineResponse(Long id, StockItemType itemType, Long variantId, Long rawMaterialId,
                                    String itemName, String sku,
                                    BigDecimal systemQuantity, BigDecimal countedQuantity, BigDecimal variance,
                                    Long adjustmentMovementId) {
        public static AuditLineResponse from(InventoryAuditLine l, String itemName, String sku) {
            return new AuditLineResponse(l.getId(), l.getItemType(), l.getVariantId(), l.getRawMaterialId(),
                    itemName, sku, l.getSystemQuantity(), l.getCountedQuantity(), l.getVariance(),
                    l.getAdjustmentMovementId());
        }
    }

    public record AuditDetailResponse(AuditResponse audit, List<AuditLineResponse> lines, int linesWithVariance) { }

    public record AuditCompletionResponse(AuditResponse audit, int adjustmentsApplied) { }
}