package com.garmentfactory.catalog.service;

import com.garmentfactory.catalog.dto.CatalogDtos.BrandResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.CategoryResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.PriceTierResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.ProductResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.VariantResponse;
import com.garmentfactory.catalog.entity.Brand;
import com.garmentfactory.catalog.entity.Category;
import com.garmentfactory.catalog.entity.Product;
import com.garmentfactory.catalog.entity.ProductVariant;
import com.garmentfactory.catalog.entity.QuantityPriceTier;

/** Entity to response conversion. Call inside a transaction: it reads lazy associations. */
public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static CategoryResponse toResponse(Category category, String parentPublicId, int depth) {
        return new CategoryResponse(category.getPublicId(), parentPublicId, category.getName(), category.getSlug(),
                category.getSortOrder(), category.isActive(), depth);
    }

    public static BrandResponse toResponse(Brand brand) {
        return new BrandResponse(brand.getPublicId(), brand.getName(), brand.isActive());
    }

    public static ProductResponse toResponse(Product p) {
        Brand brand = p.getBrand();
        return new ProductResponse(p.getPublicId(), p.getSlug(), p.getName(), p.getDescription(),
                p.getCategory().getPublicId(), p.getCategory().getName(),
                brand == null ? null : brand.getPublicId(), brand == null ? null : brand.getName(),
                p.getFabric(), p.getGsm(), p.getComposition(), p.getHsnCode(),
                p.getGstRate(), p.getBasePrice(), p.getMrp(), p.isCustomizable(), p.getStatus().name(), p.getCreatedAt());
    }

    public static VariantResponse toResponse(ProductVariant v) {
        var effective = v.getPriceOverride() != null ? v.getPriceOverride() : v.getProduct().getBasePrice();
        return new VariantResponse(v.getPublicId(), v.getSku(), v.getSize(), v.getColorName(), v.getColorHex(),
                v.getPriceOverride(), effective, v.getWeightG(), v.getBarcode(), v.getStatus().name());
    }

    public static PriceTierResponse toResponse(QuantityPriceTier t) {
        return new PriceTierResponse(t.getMinQty(), t.getMaxQty(), t.getUnitPrice());
    }
}