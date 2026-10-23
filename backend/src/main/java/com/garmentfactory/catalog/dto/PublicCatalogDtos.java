package com.garmentfactory.catalog.dto;

import com.garmentfactory.catalog.dto.CatalogDtos.PriceTierResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * What a shopper sees. Deliberately smaller than the staff responses: no SKUs, no barcodes,
 * no HSN codes and no status fields.
 */
public final class PublicCatalogDtos {

    private PublicCatalogDtos() {
    }

    public record PublicCategoryResponse(String id, String parentId, String name, String slug, int depth) {
    }

    public record PublicBrandResponse(String id, String name) {
    }

    /** One product in a listing. {@code fromPrice} is the lowest price among its active variants. */
    public record ProductCardResponse(String id, String slug, String name, String brandName,
                                      String categoryName, String categorySlug,
                                      BigDecimal fromPrice, BigDecimal mrp, boolean customizable,
                                      BigDecimal rating, int reviewCount, String imageUrl) {
    }

    public record PublicVariantResponse(String id, String size, String colorName, String colorHex, BigDecimal price) {
    }

    public record PublicImageResponse(String id, String url, String altText, boolean primary, String variantId) {
    }

    public record PublicProductResponse(String id, String slug, String name, String description,
                                        String brandName, String categoryName, String categorySlug,
                                        String fabric, Integer gsm, String composition, BigDecimal gstRate,
                                        BigDecimal fromPrice, BigDecimal mrp, boolean customizable,
                                        BigDecimal rating, int reviewCount,
                                        List<PublicVariantResponse> variants,
                                        List<PriceTierResponse> priceTiers,
                                        List<PublicImageResponse> images) {
    }

    public record ColorFacet(String name, String hex) {
    }

    /** What can be filtered on within the current category: shown as the filter choices. */
    public record FacetsResponse(List<String> sizes, List<ColorFacet> colors, BigDecimal minPrice, BigDecimal maxPrice) {
    }
}