package com.garmentfactory.catalog.service;

import static com.garmentfactory.catalog.entity.VariantStatus.ACTIVE;
import static com.garmentfactory.catalog.entity.VariantStatus.ARCHIVED;
import static com.garmentfactory.catalog.entity.VariantStatus.INACTIVE;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.catalog.dto.CatalogDtos.VariantRequest;
import com.garmentfactory.catalog.dto.CatalogDtos.VariantResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.VariantUpdateRequest;
import com.garmentfactory.catalog.entity.Product;
import com.garmentfactory.catalog.entity.ProductStatus;
import com.garmentfactory.catalog.entity.ProductVariant;
import com.garmentfactory.catalog.entity.VariantStatus;
import com.garmentfactory.catalog.repository.ProductRepository;
import com.garmentfactory.catalog.repository.ProductVariantRepository;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.statemachine.TransitionRules;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Variants (size and colour of a product). The SKU is globally unique and immutable; a product can
 * never have two variants with the same size and colour; an active product always keeps at least one
 * active variant.
 */
@Service
public class VariantService {

    static final int MAX_VARIANTS_PER_PRODUCT = 200;
    private static final Pattern SKU = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,63}$");

    static final TransitionRules<VariantStatus> TRANSITIONS = TransitionRules.forType(VariantStatus.class)
            .allow(ACTIVE, INACTIVE, ARCHIVED)
            .allow(INACTIVE, ACTIVE, ARCHIVED)
            .build();

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final AuditRecorder audit;

    public VariantService(ProductRepository products, ProductVariantRepository variants, AuditRecorder audit) {
        this.products = products;
        this.variants = variants;
        this.audit = audit;
    }

    @Transactional
    public VariantResponse create(String productPublicId, VariantRequest request) {
        Product product = findProduct(productPublicId);
        requireNotArchived(product);
        if (variants.countByProductId(product.getId()) >= MAX_VARIANTS_PER_PRODUCT) {
            throw ApiException.conflict("VARIANT_LIMIT_REACHED",
                    "A product can have at most " + MAX_VARIANTS_PER_PRODUCT + " variants.");
        }

        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (!SKU.matcher(sku).matches()) {
            throw ApiException.badRequest("INVALID_SKU",
                    "A SKU is 3 to 64 characters: letters, digits and hyphens, starting with a letter or digit.");
        }
        if (variants.existsBySkuAndIdNot(sku, 0L)) {
            throw ApiException.conflict("SKU_ALREADY_EXISTS", "This SKU is already used by another variant.");
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(sku);
        applyDetails(variant, request.size(), request.colorName(), request.colorHex(),
                request.priceOverride(), request.weightG(), request.barcode(), 0L);
        variants.save(variant);

        audit.record(AuditEvent.of("VARIANT_CREATED", "VARIANT", variant.getPublicId(),
                "sku=" + sku + ", size=" + variant.getSize() + ", color=" + variant.getColorName()));
        return CatalogMapper.toResponse(variant);
    }

    @Transactional
    public VariantResponse update(String productPublicId, String variantPublicId, VariantUpdateRequest request) {
        Product product = findProduct(productPublicId);
        requireNotArchived(product);
        ProductVariant variant = findVariant(product, variantPublicId);

        String before = priceText(variant.getPriceOverride());
        applyDetails(variant, request.size(), request.colorName(), request.colorHex(),
                request.priceOverride(), request.weightG(), request.barcode(), variant.getId());
        String after = priceText(variant.getPriceOverride());

        audit.record(AuditEvent.of("VARIANT_UPDATED", "VARIANT", variant.getPublicId(),
                "sku=" + variant.getSku() + (before.equals(after) ? "" : ", priceOverride " + before + " -> " + after)));
        return CatalogMapper.toResponse(variant);
    }

    @Transactional
    public VariantResponse changeStatus(String productPublicId, String variantPublicId, String status) {
        Product product = findProduct(productPublicId);
        ProductVariant variant = findVariant(product, variantPublicId);
        VariantStatus target = parseStatus(status);
        VariantStatus current = variant.getStatus();
        TRANSITIONS.require(current, target, "A variant");

        boolean leavingActive = current == ACTIVE && target != ACTIVE;
        if (leavingActive && product.getStatus() == ProductStatus.ACTIVE
                && variants.countByProductIdAndStatus(product.getId(), ACTIVE) <= 1) {
            throw ApiException.conflict("LAST_ACTIVE_VARIANT",
                    "An active product needs at least one active variant. Deactivate the product first.");
        }
        variant.setStatus(target);
        audit.record(AuditEvent.of("VARIANT_STATUS_CHANGED", "VARIANT", variant.getPublicId(),
                "sku=" + variant.getSku() + ", " + current + " -> " + target));
        return CatalogMapper.toResponse(variant);
    }

    private void applyDetails(ProductVariant variant, String size, String colorName, String colorHex,
                              BigDecimal priceOverride, Integer weightG, String barcode, Long ownId) {
        String canonicalSize = Sizes.canonical(size).orElseThrow(() ->
                ApiException.badRequest("INVALID_SIZE", "Size must be one of " + Sizes.all() + "."));
        String color = colorName.trim();
        String code = barcode == null || barcode.isBlank() ? null : barcode.trim();

        if (variants.existsByProductIdAndSizeAndColorNameAndIdNot(variant.getProduct().getId(), canonicalSize, color, ownId)) {
            throw ApiException.conflict("VARIANT_ALREADY_EXISTS", "This product already has a variant in that size and colour.");
        }
        if (code != null && variants.existsByBarcodeAndIdNot(code, ownId)) {
            throw ApiException.conflict("BARCODE_ALREADY_EXISTS", "This barcode is already used by another variant.");
        }
        variant.setSize(canonicalSize);
        variant.setColorName(color);
        variant.setColorHex(colorHex == null || colorHex.isBlank() ? null : colorHex.toUpperCase(Locale.ROOT));
        variant.setPriceOverride(priceOverride == null ? null : priceOverride.setScale(2));
        variant.setWeightG(weightG);
        variant.setBarcode(code);
    }

    private Product findProduct(String publicId) {
        return products.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found."));
    }

    private ProductVariant findVariant(Product product, String variantPublicId) {
        return variants.findByPublicIdAndProductId(variantPublicId, product.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variant not found."));
    }

    private static void requireNotArchived(Product product) {
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw ApiException.conflict("PRODUCT_ARCHIVED", "An archived product cannot be changed. Restore it to draft first.");
        }
    }

    private static VariantStatus parseStatus(String status) {
        try {
            return VariantStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_STATUS", "Status must be one of ACTIVE, INACTIVE, ARCHIVED.");
        }
    }

    private static String priceText(BigDecimal price) {
        return price == null ? "none" : price.toPlainString();
    }
}