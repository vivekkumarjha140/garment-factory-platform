package com.garmentfactory.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Requests and responses of the staff catalog API. Ids in these records are public ids (UUIDs). */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ---- categories and brands ----

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            String parentId,
            @Min(0) @Max(9999) Integer sortOrder,
            Boolean active) {
    }

    public record CategoryResponse(String id, String parentId, String name, String slug, int sortOrder,
                                   boolean active, int depth) {
    }

    public record BrandRequest(@NotBlank @Size(max = 100) String name, Boolean active) {
    }

    public record BrandResponse(String id, String name, boolean active) {
    }

    // ---- products ----

    public record ProductRequest(
            @NotBlank String categoryId,
            String brandId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description,
            @Size(max = 100) String fabric,
            @Min(1) @Max(1000) Integer gsm,
            @Size(max = 200) String composition,
            @Pattern(regexp = "^[0-9]{4,8}$", message = "must be 4 to 8 digits") String hsnCode,
            @NotNull @DecimalMin("0.00") @DecimalMax("28.00") @Digits(integer = 2, fraction = 2) BigDecimal gstRate,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal mrp,
            Boolean customizable) {
    }

    public record ProductResponse(String id, String slug, String name, String description,
                                  String categoryId, String categoryName, String brandId, String brandName,
                                  String fabric, Integer gsm, String composition, String hsnCode,
                                  BigDecimal gstRate, BigDecimal basePrice, BigDecimal mrp,
                                  boolean customizable, String status, Instant createdAt) {
    }

    public record ProductDetailResponse(ProductResponse product, List<VariantResponse> variants,
                                        List<PriceTierResponse> priceTiers) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    // ---- variants ----

    public record VariantRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 20) String size,
            @NotBlank @Size(max = 50) String colorName,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must look like #1A2B3C") String colorHex,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal priceOverride,
            @Min(1) @Max(5000) Integer weightG,
            @Size(max = 32) String barcode) {
    }

    /** The SKU is not editable: stock and order history refer to it. */
    public record VariantUpdateRequest(
            @NotBlank @Size(max = 20) String size,
            @NotBlank @Size(max = 50) String colorName,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must look like #1A2B3C") String colorHex,
            @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal priceOverride,
            @Min(1) @Max(5000) Integer weightG,
            @Size(max = 32) String barcode) {
    }

    public record VariantResponse(String id, String sku, String size, String colorName, String colorHex,
                                  BigDecimal priceOverride, BigDecimal effectivePrice, Integer weightG,
                                  String barcode, String status) {
    }

    // ---- quantity price tiers ----

    public record TierRequest(
            @NotNull @Min(1) Integer minQty,
            @Min(1) Integer maxQty,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {
    }

    /** Replaces all tiers of the product. An empty list removes them. */
    public record PriceTiersRequest(@NotNull @Size(max = 10) List<@Valid TierRequest> tiers) {
    }

    public record PriceTierResponse(int minQty, Integer maxQty, BigDecimal unitPrice) {
    }
}