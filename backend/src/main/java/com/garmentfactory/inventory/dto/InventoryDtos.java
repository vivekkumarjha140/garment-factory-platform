package com.garmentfactory.inventory.dto;

import com.garmentfactory.inventory.entity.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public final class InventoryDtos {

    private InventoryDtos() { }

    // ------------------------------------------------------------ paging

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }

        /** Page size is capped at 100 to protect the DB. */
        public static Pageable pageable(int page, int size) {
            return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        }
    }

    // ------------------------------------------------------------ finished goods

    public record StockResponse(String publicId, Long variantId, int onHand, int reserved, int damaged,
                                int available, int lowStockThreshold, boolean lowStock,
                                long version, Instant updatedAt) {
        public static StockResponse from(FinishedGoodsStock s) {
            return new StockResponse(s.getPublicId(), s.getVariantId(), s.getOnHand(), s.getReserved(),
                    s.getDamaged(), s.getAvailable(), s.getLowStockThreshold(), s.isLowStock(),
                    s.getVersion(), s.getUpdatedAt());
        }
    }

    public record InitializeStockRequest(@NotNull Long variantId, @Min(0) Integer lowStockThreshold) { }

    public record ThresholdRequest(@NotNull @Min(0) Integer lowStockThreshold) { }

    public record ReceiveStockRequest(@Min(1) int quantity,
                                      @DecimalMin("0.0") BigDecimal unitCost,
                                      @Size(max = 150) String supplierName,
                                      @Size(max = 100) String supplierReference,
                                      @Size(max = 500) String notes) { }

    public record AdjustStockRequest(@NotNull Integer delta,
                                     @NotBlank @Size(max = 500) String reason) { }

    public record DamageStockRequest(@Min(1) int quantity,
                                     @NotBlank @Size(max = 500) String reason) { }

    // ------------------------------------------------------------ reservations

    public record ReserveRequest(@NotNull Long orderId, @NotNull Long variantId, @Min(1) int quantity) { }

    public record ReservationResponse(String publicId, Long orderId, Long variantId, int quantity,
                                      ReservationStatus status, Instant expiresAt) {
        public static ReservationResponse from(StockReservation r) {
            return new ReservationResponse(r.getPublicId(), r.getOrderId(), r.getVariantId(),
                    r.getQuantity(), r.getStatus(), r.getExpiresAt());
        }
    }

    public record ReleaseResponse(Long orderId, int released) { }

    // ------------------------------------------------------------ raw materials

    public record RawMaterialResponse(Long id, String publicId, String code, String name,
                                      RawMaterialCategory category, String uom,
                                      BigDecimal onHand, BigDecimal reserved, BigDecimal available,
                                      BigDecimal lowStockThreshold, boolean lowStock,
                                      BigDecimal unitCost, boolean active, long version) {
        public static RawMaterialResponse from(RawMaterial r) {
            return new RawMaterialResponse(r.getId(), r.getPublicId(), r.getCode(), r.getName(),
                    r.getCategory(), r.getUom(), r.getOnHand(), r.getReserved(), r.getAvailable(),
                    r.getLowStockThreshold(), r.isLowStock(), r.getUnitCost(), r.isActive(), r.getVersion());
        }
    }

    public record CreateRawMaterialRequest(@NotBlank @Size(max = 40) String code,
                                           @NotBlank @Size(max = 150) String name,
                                           @NotNull RawMaterialCategory category,
                                           @NotBlank @Size(max = 20) String uom,
                                           @DecimalMin("0.0") BigDecimal lowStockThreshold,
                                           @DecimalMin("0.0") BigDecimal unitCost) { }

    public record ReceiveRawMaterialRequest(@NotNull @DecimalMin("0.001") BigDecimal quantity,
                                            @DecimalMin("0.0") BigDecimal unitCost,
                                            @Size(max = 150) String supplierName,
                                            @Size(max = 100) String supplierReference,
                                            @Size(max = 500) String notes) { }

    public record AdjustRawMaterialRequest(@NotNull BigDecimal delta,
                                           @NotBlank @Size(max = 500) String reason) { }

    // ------------------------------------------------------------ ledger / receipts

    public record MovementResponse(String publicId, StockItemType itemType, Long variantId, Long rawMaterialId,
                                   MovementType movementType, BigDecimal onHandDelta, BigDecimal reservedDelta,
                                   BigDecimal damagedDelta, BigDecimal balanceOnHandAfter,
                                   String referenceType, String referenceId, String reason,
                                   Long actorUserId, Instant createdAt) {
        public static MovementResponse from(StockMovement m) {
            return new MovementResponse(m.getPublicId(), m.getItemType(), m.getVariantId(), m.getRawMaterialId(),
                    m.getMovementType(), m.getOnHandDelta(), m.getReservedDelta(), m.getDamagedDelta(),
                    m.getBalanceOnHandAfter(), m.getReferenceType(), m.getReferenceId(), m.getReason(),
                    m.getActorUserId(), m.getCreatedAt());
        }
    }

    public record ReceiptResponse(String publicId, String receiptNumber, StockItemType itemType,
                                  Long variantId, Long rawMaterialId, BigDecimal quantity, BigDecimal unitCost,
                                  String supplierName, String supplierReference, String notes,
                                  Instant receivedAt, Long receivedByUserId) {
        public static ReceiptResponse from(StockReceipt r) {
            return new ReceiptResponse(r.getPublicId(), r.getReceiptNumber(), r.getItemType(),
                    r.getVariantId(), r.getRawMaterialId(), r.getQuantity(), r.getUnitCost(),
                    r.getSupplierName(), r.getSupplierReference(), r.getNotes(),
                    r.getReceivedAt(), r.getReceivedByUserId());
        }
    }
}