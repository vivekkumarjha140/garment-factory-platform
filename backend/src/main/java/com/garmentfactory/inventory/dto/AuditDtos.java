
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

    private AuditDtos() {
    }

    // ------------------------------------------------------------
    // Requests
    // ------------------------------------------------------------

    public record StartAuditRequest(
            @Size(max = 500)
            String notes
    ) {
    }

    public record CountFinishedGoodRequest(
            @NotNull
            Long variantId,

            @NotNull
            @Min(0)
            Integer countedQuantity
    ) {
    }

    public record CountRawMaterialRequest(
            @NotNull
            Long rawMaterialId,

            @NotNull
            @DecimalMin("0.000")
            BigDecimal countedQuantity
    ) {
    }

    public record CompleteAuditRequest(
            @NotNull
            Boolean applyAdjustments
    ) {
    }

    // ------------------------------------------------------------
    // Responses
    // ------------------------------------------------------------

    public record AuditResponse(
            Long id,
            String publicId,
            String auditNumber,
            AuditStatus status,
            String notes,
            Long startedByUserId,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {

        public static AuditResponse from(InventoryAudit audit) {
            return new AuditResponse(
                    audit.getId(),
                    audit.getPublicId(),
                    audit.getAuditNumber(),
                    audit.getStatus(),
                    audit.getNotes(),
                    audit.getStartedByUserId(),
                    audit.getStartedAt(),
                    audit.getCompletedAt(),
                    audit.getCreatedAt(),
                    audit.getUpdatedAt());
        }
    }

    public record AuditLineResponse(
            Long id,
            Long auditId,
            StockItemType itemType,
            Long variantId,
            Long rawMaterialId,
            BigDecimal systemQuantity,
            BigDecimal countedQuantity,
            BigDecimal variance,
            Long adjustmentMovementId,
            Instant createdAt
    ) {

        public static AuditLineResponse from(InventoryAuditLine line) {
            return new AuditLineResponse(
                    line.getId(),
                    line.getAuditId(),
                    line.getItemType(),
                    line.getVariantId(),
                    line.getRawMaterialId(),
                    line.getSystemQuantity(),
                    line.getCountedQuantity(),
                    line.getVariance(),
                    line.getAdjustmentMovementId(),
                    line.getCreatedAt());
        }
    }

    public record AuditDetailResponse(
            AuditResponse audit,
            List<AuditLineResponse> lines,
            List<AuditLineResponse> linesWithVariance
    ) {
    }

    public record AuditCompletionResponse(
            AuditResponse audit,
            int adjustmentsApplied
    ) {
    }
}
