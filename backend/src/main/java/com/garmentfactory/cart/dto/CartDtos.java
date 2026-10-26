package com.garmentfactory.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** What the shopper sends and sees. No SKUs and no internal variant ids, same as the public catalog. */
public final class CartDtos {

    private CartDtos() {
    }

    /** variantId is the variant's public id (the "id" in the public product response). */
    public record AddItemRequest(
            @NotBlank @Size(max = 36) String variantId,
            @Min(1) @Max(100000) int quantity) {
    }

    public record UpdateQuantityRequest(@Min(1) @Max(100000) int quantity) {
    }

    public record ApplyCouponRequest(@NotBlank @Size(max = 40) String code) {
    }

    /** issue is null, or one of UNAVAILABLE, OUT_OF_STOCK, INSUFFICIENT_STOCK. */
    public record CartLineResponse(long itemId, String variantId, String productName, String productSlug,
                                   String size, String colorName, int quantity,
                                   BigDecimal unitPrice, BigDecimal lineTotal,
                                   BigDecimal gstRate, BigDecimal gstAmount,
                                   int availableStock, String issue) {
    }

    public record CouponResponse(String code, String description, String type, BigDecimal discount,
                                 boolean freeShipping) {
    }

    public record ShippingResponse(String zoneName, boolean freeShippingApplied,
                                   int estimatedDaysMin, int estimatedDaysMax) {
    }

    /** shippingCharge is null until a delivery address is known (shippingCalculated=false). */
    public record TotalsResponse(int totalQuantity, BigDecimal subtotal, BigDecimal discount,
                                 BigDecimal taxableAmount, BigDecimal gstAmount,
                                 BigDecimal shippingCharge, boolean shippingCalculated, BigDecimal total) {
    }

    public record CartResponse(List<CartLineResponse> lines, CouponResponse coupon, String couponError,
                               ShippingResponse shipping, String shippingError, TotalsResponse totals,
                               boolean checkoutReady, String shippingAddressId) {
    }
}