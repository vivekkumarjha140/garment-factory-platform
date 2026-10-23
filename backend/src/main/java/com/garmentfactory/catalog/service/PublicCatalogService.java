package com.garmentfactory.catalog.service;

import com.garmentfactory.catalog.dto.CatalogDtos.PriceTierResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.ColorFacet;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.FacetsResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.ProductCardResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.PublicBrandResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.PublicCategoryResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.PublicImageResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.PublicProductResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.PublicVariantResponse;
import com.garmentfactory.catalog.entity.Brand;
import com.garmentfactory.catalog.entity.Category;
import com.garmentfactory.catalog.entity.Product;
import com.garmentfactory.catalog.entity.ProductStatus;
import com.garmentfactory.catalog.entity.ProductVariant;
import com.garmentfactory.catalog.entity.VariantStatus;
import com.garmentfactory.catalog.repository.BrandRepository;
import com.garmentfactory.catalog.repository.CatalogFilter;
import com.garmentfactory.catalog.repository.CatalogSort;
import com.garmentfactory.catalog.repository.CategoryRepository;
import com.garmentfactory.catalog.repository.ProductImageRepository;
import com.garmentfactory.catalog.repository.ProductRepository;
import com.garmentfactory.catalog.repository.ProductVariantRepository;
import com.garmentfactory.catalog.repository.PublicCatalogRepository;
import com.garmentfactory.catalog.repository.QuantityPriceTierRepository;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.web.PageResponse;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shop's read side. A category is visible only if it and all its ancestors are active, and every
 * query is restricted to visible categories, so hiding "Men" hides everything below it as well.
 */
@Service
public class PublicCatalogService {

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_FILTER_VALUES = 10;
    private static final String IMAGE_URL = "/api/v1/catalog/images/";

    private final PublicCatalogRepository catalog;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final ProductVariantRepository variants;
    private final QuantityPriceTierRepository tiers;
    private final ProductImageRepository images;

    public PublicCatalogService(PublicCatalogRepository catalog, ProductRepository products,
                                CategoryRepository categories, BrandRepository brands,
                                ProductVariantRepository variants, QuantityPriceTierRepository tiers,
                                ProductImageRepository images) {
        this.catalog = catalog;
        this.products = products;
        this.categories = categories;
        this.brands = brands;
        this.variants = variants;
        this.tiers = tiers;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public List<PublicCategoryResponse> categories() {
        List<Category> all = categories.findAllByOrderBySortOrderAscNameAsc();
        Map<Long, Category> byId = all.stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        CategoryTree tree = treeOf(all);
        return all.stream()
                .filter(category -> isVisible(category, byId))
                .map(category -> new PublicCategoryResponse(category.getPublicId(),
                        category.getParentId() == null ? null : byId.get(category.getParentId()).getPublicId(),
                        category.getName(), category.getSlug(), tree.depth(category.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicBrandResponse> brands() {
        return brands.findAllByOrderByNameAsc().stream()
                .filter(Brand::isActive)
                .map(brand -> new PublicBrandResponse(brand.getPublicId(), brand.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductCardResponse> search(String query, String categorySlug, String brandPublicId,
                                                    BigDecimal minPrice, BigDecimal maxPrice,
                                                    List<String> sizes, List<String> colors,
                                                    Boolean customizable, String sort, Pageable pageable) {
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
            throw ApiException.badRequest("INVALID_PRICE_RANGE", "The price range is not valid.");
        }
        CatalogSort order = CatalogSort.parse(sort);
        Set<Long> scope = categoryScope(categorySlug);
        if (scope.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }

        Long brandId = null;
        if (brandPublicId != null && !brandPublicId.isBlank()) {
            Brand brand = brands.findByPublicId(brandPublicId.trim())
                    .filter(Brand::isActive)
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_BRAND", "The brand does not exist."));
            brandId = brand.getId();
        }

        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (text.length() > MAX_QUERY_LENGTH) {
            text = text.substring(0, MAX_QUERY_LENGTH);
        }
        CatalogFilter filter = new CatalogFilter(text.isEmpty() ? null : "%" + text + "%", scope, brandId,
                minPrice, maxPrice, canonicalSizes(sizes), cleanColors(colors), customizable, order);

        Page<Product> page = catalog.search(filter, pageable);
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        Map<Long, BigDecimal> prices = catalog.fromPrices(ids);
        Map<Long, String> imageIds = catalog.primaryImageIds(ids);
        return PageResponse.from(page.map(product -> toCard(product, prices, imageIds)));
    }

    @Transactional(readOnly = true)
    public PublicProductResponse detail(String slug) {
        Product product = products.findPublicBySlug(slug.trim().toLowerCase(Locale.ROOT), ProductStatus.ACTIVE)
                .orElseThrow(PublicCatalogService::productNotFound);
        if (!visibleCategoryIds().contains(product.getCategory().getId())) {
            throw productNotFound();
        }
        List<ProductVariant> active = variants.findByProductIdAndStatusOrderByCreatedAtAscIdAsc(product.getId(), VariantStatus.ACTIVE)
                .stream()
                .sorted(Comparator.comparingInt((ProductVariant v) -> sizeRank(v.getSize()))
                        .thenComparing(ProductVariant::getColorName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (active.isEmpty()) {
            throw productNotFound();
        }

        List<PublicVariantResponse> variantResponses = active.stream()
                .map(v -> new PublicVariantResponse(v.getPublicId(), v.getSize(), v.getColorName(), v.getColorHex(),
                        v.getPriceOverride() != null ? v.getPriceOverride() : product.getBasePrice()))
                .toList();
        BigDecimal fromPrice = variantResponses.stream().map(PublicVariantResponse::price).min(BigDecimal::compareTo).orElseThrow();
        List<PriceTierResponse> tierResponses = tiers.findByProductIdOrderByMinQtyAsc(product.getId()).stream()
                .map(CatalogMapper::toResponse).toList();
        List<PublicImageResponse> imageResponses = images.findByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream()
                .map(image -> new PublicImageResponse(image.getPublicId(), IMAGE_URL + image.getPublicId(), image.getAltText(),
                        image.isPrimaryImage(), image.getVariant() == null ? null : image.getVariant().getPublicId()))
                .toList();

        Brand brand = product.getBrand();
        return new PublicProductResponse(product.getPublicId(), product.getSlug(), product.getName(), product.getDescription(),
                brand == null ? null : brand.getName(), product.getCategory().getName(), product.getCategory().getSlug(),
                product.getFabric(), product.getGsm(), product.getComposition(), product.getGstRate(),
                fromPrice, product.getMrp(), product.isCustomizable(), product.getAvgRating(), product.getReviewCount(),
                variantResponses, tierResponses, imageResponses);
    }

    @Transactional(readOnly = true)
    public FacetsResponse facets(String categorySlug) {
        Set<Long> scope = categoryScope(categorySlug);
        if (scope.isEmpty()) {
            return new FacetsResponse(List.of(), List.of(), null, null);
        }
        PublicCatalogRepository.Facets facets = catalog.facets(scope);
        return new FacetsResponse(facets.sizes(),
                facets.colors().stream().map(color -> new ColorFacet(color.name(), color.hex())).toList(),
                facets.minPrice(), facets.maxPrice());
    }

    /** All visible categories, or the chosen category and everything visible below it. */
    private Set<Long> categoryScope(String categorySlug) {
        List<Category> all = categories.findAll();
        Map<Long, Category> byId = all.stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        Set<Long> visible = all.stream().filter(category -> isVisible(category, byId)).map(Category::getId)
                .collect(Collectors.toCollection(HashSet::new));
        if (categorySlug == null || categorySlug.isBlank()) {
            return visible;
        }
        Category chosen = all.stream().filter(category -> category.getSlug().equalsIgnoreCase(categorySlug.trim()))
                .findFirst()
                .filter(category -> visible.contains(category.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category not found."));
        Set<Long> subtree = new HashSet<>(treeOf(all).descendantsAndSelf(chosen.getId()));
        subtree.retainAll(visible);
        return subtree;
    }

    private Set<Long> visibleCategoryIds() {
        List<Category> all = categories.findAll();
        Map<Long, Category> byId = all.stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        return all.stream().filter(category -> isVisible(category, byId)).map(Category::getId).collect(Collectors.toSet());
    }

    private static boolean isVisible(Category category, Map<Long, Category> byId) {
        Category current = category;
        for (int steps = 0; current != null && steps < 20; steps++) {
            if (!current.isActive()) {
                return false;
            }
            current = current.getParentId() == null ? null : byId.get(current.getParentId());
        }
        return true;
    }

    private static CategoryTree treeOf(List<Category> all) {
        return new CategoryTree(all.stream().map(c -> new CategoryTree.Node(c.getId(), c.getParentId())).toList());
    }

    private static List<String> canonicalSizes(List<String> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        Set<String> result = new LinkedHashSet<>();
        for (String size : sizes.stream().limit(MAX_FILTER_VALUES).toList()) {
            result.add(Sizes.canonical(size).orElseThrow(() ->
                    ApiException.badRequest("INVALID_SIZE", "Size must be one of " + Sizes.all() + ".")));
        }
        return List.copyOf(result);
    }

    private static List<String> cleanColors(List<String> colors) {
        if (colors == null || colors.isEmpty()) {
            return null;
        }
        List<String> cleaned = colors.stream()
                .filter(color -> color != null && !color.isBlank())
                .map(String::trim)
                .filter(color -> color.length() <= 50)
                .distinct()
                .limit(MAX_FILTER_VALUES)
                .toList();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static int sizeRank(String size) {
        int index = Sizes.all().indexOf(size);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private static ProductCardResponse toCard(Product product, Map<Long, BigDecimal> prices, Map<Long, String> imageIds) {
        String imageId = imageIds.get(product.getId());
        Brand brand = product.getBrand();
        return new ProductCardResponse(product.getPublicId(), product.getSlug(), product.getName(),
                brand == null ? null : brand.getName(), product.getCategory().getName(), product.getCategory().getSlug(),
                prices.getOrDefault(product.getId(), product.getBasePrice()), product.getMrp(), product.isCustomizable(),
                product.getAvgRating(), product.getReviewCount(), imageId == null ? null : IMAGE_URL + imageId);
    }

    private static ApiException productNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found.");
    }
}