package com.garmentfactory.inventory.dto;

import com.garmentfactory.catalog.api.VariantSummary;
import com.garmentfactory.inventory.entity.FinishedGoodsStock;
import jakarta.validation.constraints.Min;
import java.time.Instant;

public final class StockOverviewDtos {

    private StockOverviewDtos() { }

    /** One dashboard row: stock counters plus the variant's SKU and product details (flat for tables). */
    public record StockItemResponse(Long variantId, String sku, String productName, String size, String colorName,
                                    String variantStatus, String publicId, int onHand, int reserved, int damaged,
                                    int available, int lowStockThreshold, boolean lowStock, long version,
                                    Instant updatedAt) {
        public static StockItemResponse from(FinishedGoodsStock s, VariantSummary v) {
            return new StockItemResponse(s.getVariantId(),
                    v == null ? null : v.sku(), v == null ? null : v.productName(),
                    v == null ? null : v.size(), v == null ? null : v.colorName(), v == null ? null : v.status(),
                    s.getPublicId(), s.getOnHand(), s.getReserved(), s.getDamaged(), s.getAvailable(),
                    s.getLowStockThreshold(), s.isLowStock(), s.getVersion(), s.getUpdatedAt());
        }
    }

    public record UninitializedVariantResponse(long variantId, String sku, String productName,
                                               String size, String colorName) {
        public static UninitializedVariantResponse from(VariantSummary v) {
            return new UninitializedVariantResponse(v.id(), v.sku(), v.productName(), v.size(), v.colorName());
        }
    }

    public record InitializeMissingRequest(@Min(0) Integer lowStockThreshold) { }

    public record InitializeMissingResponse(int created) { }
}