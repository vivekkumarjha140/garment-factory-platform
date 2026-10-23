package com.garmentfactory.catalog.service;

import static com.garmentfactory.catalog.entity.ProductStatus.ACTIVE;
import static com.garmentfactory.catalog.entity.ProductStatus.ARCHIVED;
import static com.garmentfactory.catalog.entity.ProductStatus.DRAFT;
import static com.garmentfactory.catalog.entity.ProductStatus.INACTIVE;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.catalog.dto.CatalogDtos.ProductDetailResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.ProductRequest;
import com.garmentfactory.catalog.dto.CatalogDtos.ProductResponse;
import com.garmentfactory.catalog.entity.Brand;
import com.garmentfactory.catalog.entity.Category;
import com.garmentfactory.catalog.entity.Product;
import com.garmentfactory.catalog.entity.ProductStatus;
import com.garmentfactory.catalog.entity.VariantStatus;
import com.garmentfactory.catalog.repository.BrandRepository;
import com.garmentfactory.catalog.repository.CategoryRepository;
import com.garmentfactory.catalog.repository.ProductRepository;
import com.garmentfactory.catalog.repository.ProductVariantRepository;
import com.garmentfactory.catalog.repository.QuantityPriceTierRepository;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.statemachine.TransitionRules;
import com.garmentfactory.shared.web.PageResponse;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff-facing product management: create, edit, publish and archive. Prices are entered here and
 * every price change is written to the audit log with the old and new value.
 */
@Service
public class ProductService {

    private static final int MAX_QUERY_LENGTH = 100;

    static final TransitionRules<ProductStatus> TRANSITIONS = TransitionRules.forType(ProductStatus.class)
            .allow(DRAFT, ACTIVE, ARCHIVED)
            .allow(ACTIVE, INACTIVE, ARCHIVED)
            .allow(INACTIVE, ACTIVE, ARCHIVED)
            .allow(ARCHIVED, DRAFT)
            .build();

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final ProductVariantRepository variants;
    private final QuantityPriceTierRepository tiers;
    private final AuditRecorder audit;

    public ProductService(ProductRepository products, CategoryRepository categories, BrandRepository brands,
                          ProductVariantRepository variants, QuantityPriceTierRepository tiers, AuditRecorder audit) {
        this.products = products;
        this.categories = categories;
        this.brands = brands;
        this.variants = variants;
        this.tiers = tiers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String query, String status, String categoryPublicId, Pageable pageable) {
        boolean anyStatus = status == null || status.isBlank();
        ProductStatus statusFilter = anyStatus ? DRAFT : parseStatus(status);
        boolean anyCategory = categoryPublicId == null || categoryPublicId.isBlank();
        Long categoryId = anyCategory ? 0L : categories.findByPublicId(categoryPublicId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_CATEGORY", "The category does not exist.")).getId();

        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (text.length() > MAX_QUERY_LENGTH) {
            text = text.substring(0, MAX_QUERY_LENGTH);
        }
        Page<Product> page = products.search(anyStatus, statusFilter, anyCategory, categoryId, "%" + text + "%", pageable);
        return PageResponse.from(page.map(CatalogMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse get(String publicId) {
        Product product = find(publicId);
        return new ProductDetailResponse(
                CatalogMapper.toResponse(product),
                variants.findByProductIdOrderByCreatedAtAscIdAsc(product.getId()).stream().map(CatalogMapper::toResponse).toList(),
                tiers.findByProductIdOrderByMinQtyAsc(product.getId()).stream().map(CatalogMapper::toResponse).toList());
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = resolveCategory(request.categoryId());
        if (!category.isActive()) {
            throw ApiException.badRequest("CATEGORY_INACTIVE", "Choose an active category.");
        }
        Brand brand = resolveBrand(request.brandId());
        requirePrices(request);

        Product product = new Product();
        apply(product, request, category, brand);
        product.setSlug(Slugs.unique(request.name(), products::existsBySlug));
        product.setStatus(DRAFT);
        products.save(product);

        audit.record(AuditEvent.of("PRODUCT_CREATED", "PRODUCT", product.getPublicId(),
                "name=" + product.getName() + ", basePrice=" + product.getBasePrice().toPlainString()
                        + ", mrp=" + product.getMrp().toPlainString()));
        return CatalogMapper.toResponse(product);
    }

    @Transactional
    public ProductResponse update(String publicId, ProductRequest request) {
        Product product = find(publicId);
        requireNotArchived(product);

        Category category = resolveCategory(request.categoryId());
        if (!category.getId().equals(product.getCategory().getId()) && !category.isActive()) {
            throw ApiException.badRequest("CATEGORY_INACTIVE", "Choose an active category.");
        }
        Brand brand = resolveBrand(request.brandId());
        requirePrices(request);

        BigDecimal newBase = request.basePrice().setScale(2);
        boolean tierAboveNewPrice = tiers.findByProductIdOrderByMinQtyAsc(product.getId()).stream()
                .anyMatch(tier -> tier.getUnitPrice().compareTo(newBase) > 0);
        if (tierAboveNewPrice) {
            throw ApiException.conflict("PRICE_TIERS_CONFLICT",
                    "A quantity price tier is higher than the new base price. Update the tiers first.");
        }

        String before = "basePrice=" + product.getBasePrice().toPlainString() + ", mrp=" + product.getMrp().toPlainString();
        apply(product, request, category, brand);
        String after = "basePrice=" + product.getBasePrice().toPlainString() + ", mrp=" + product.getMrp().toPlainString();

        audit.record(AuditEvent.of("PRODUCT_UPDATED", "PRODUCT", product.getPublicId(),
                before.equals(after) ? "Details updated; prices unchanged (" + after + ")" : "Prices changed: " + before + " -> " + after));
        return CatalogMapper.toResponse(product);
    }

    @Transactional
    public ProductResponse changeStatus(String publicId, String status) {
        Product product = find(publicId);
        ProductStatus target = parseStatus(status);
        ProductStatus current = product.getStatus();
        TRANSITIONS.require(current, target, "A product");

        if (target == ACTIVE) {
            if (!product.getCategory().isActive()) {
                throw ApiException.conflict("CATEGORY_INACTIVE", "The product's category is inactive.");
            }
            if (variants.countByProductIdAndStatus(product.getId(), VariantStatus.ACTIVE) == 0) {
                throw ApiException.conflict("NO_ACTIVE_VARIANT", "Add at least one active variant before publishing.");
            }
        }
        product.setStatus(target);
        audit.record(AuditEvent.of("PRODUCT_STATUS_CHANGED", "PRODUCT", product.getPublicId(), current + " -> " + target));
        return CatalogMapper.toResponse(product);
    }

    private Product find(String publicId) {
        return products.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found."));
    }

    private Category resolveCategory(String publicId) {
        return categories.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_CATEGORY", "The category does not exist."));
    }

    private Brand resolveBrand(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        Brand brand = brands.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_BRAND", "The brand does not exist."));
        if (!brand.isActive()) {
            throw ApiException.badRequest("BRAND_INACTIVE", "Choose an active brand.");
        }
        return brand;
    }

    private static void requirePrices(ProductRequest request) {
        if (request.mrp().compareTo(request.basePrice()) < 0) {
            throw ApiException.badRequest("INVALID_PRICE", "The MRP cannot be lower than the selling price.");
        }
    }

    private static void requireNotArchived(Product product) {
        if (product.getStatus() == ARCHIVED) {
            throw ApiException.conflict("PRODUCT_ARCHIVED", "An archived product cannot be edited. Restore it to draft first.");
        }
    }

    private static void apply(Product product, ProductRequest r, Category category, Brand brand) {
        product.setCategory(category);
        product.setBrand(brand);
        product.setName(r.name().trim());
        product.setDescription(blankToNull(r.description()));
        product.setFabric(blankToNull(r.fabric()));
        product.setGsm(r.gsm());
        product.setComposition(blankToNull(r.composition()));
        product.setHsnCode(blankToNull(r.hsnCode()));
        product.setGstRate(r.gstRate().setScale(2));
        product.setBasePrice(r.basePrice().setScale(2));
        product.setMrp(r.mrp().setScale(2));
        product.setCustomizable(Boolean.TRUE.equals(r.customizable()));
    }

    private static ProductStatus parseStatus(String status) {
        try {
            return ProductStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_STATUS", "Status must be one of DRAFT, ACTIVE, INACTIVE, ARCHIVED.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}