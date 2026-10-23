package com.garmentfactory.catalog.controller;

import com.garmentfactory.catalog.dto.CatalogDtos.PriceTierResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.PriceTiersRequest;
import com.garmentfactory.catalog.dto.CatalogDtos.ProductDetailResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.ProductRequest;
import com.garmentfactory.catalog.dto.CatalogDtos.ProductResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.StatusRequest;
import com.garmentfactory.catalog.dto.CatalogDtos.VariantRequest;
import com.garmentfactory.catalog.dto.CatalogDtos.VariantResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.VariantUpdateRequest;
import com.garmentfactory.catalog.service.PriceTierService;
import com.garmentfactory.catalog.service.ProductService;
import com.garmentfactory.catalog.service.VariantService;
import com.garmentfactory.shared.web.PageResponse;
import com.garmentfactory.shared.web.Paging;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff management of products, variants and quantity price tiers. */
@RestController
@RequestMapping("/api/v1/admin/catalog/products")
@Tag(name = "Admin: products")
public class AdminProductController {

    private final ProductService products;
    private final VariantService variants;
    private final PriceTierService priceTiers;

    public AdminProductController(ProductService products, VariantService variants, PriceTierService priceTiers) {
        this.products = products;
        this.variants = variants;
        this.priceTiers = priceTiers;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String categoryId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return products.search(q, status, categoryId, Paging.of(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ProductDetailResponse get(@PathVariable String id) {
        return products.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return products.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ProductResponse changeStatus(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        return products.changeStatus(id, request.status());
    }

    @PostMapping("/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public VariantResponse createVariant(@PathVariable String id, @Valid @RequestBody VariantRequest request) {
        return variants.create(id, request);
    }

    @PutMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public VariantResponse updateVariant(@PathVariable String id, @PathVariable String variantId,
                                         @Valid @RequestBody VariantUpdateRequest request) {
        return variants.update(id, variantId, request);
    }

    @PostMapping("/{id}/variants/{variantId}/status")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public VariantResponse changeVariantStatus(@PathVariable String id, @PathVariable String variantId,
                                               @Valid @RequestBody StatusRequest request) {
        return variants.changeStatus(id, variantId, request.status());
    }

    @GetMapping("/{id}/price-tiers")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<PriceTierResponse> priceTiers(@PathVariable String id) {
        return priceTiers.get(id);
    }

    @PutMapping("/{id}/price-tiers")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public List<PriceTierResponse> replacePriceTiers(@PathVariable String id, @Valid @RequestBody PriceTiersRequest request) {
        return priceTiers.replace(id, request);
    }
}