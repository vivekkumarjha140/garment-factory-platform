package com.garmentfactory.catalog.controller;

import com.garmentfactory.catalog.dto.PublicCatalogDtos.FacetsResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.ProductCardResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.PublicBrandResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.PublicCategoryResponse;
import com.garmentfactory.catalog.dto.PublicCatalogDtos.PublicProductResponse;
import com.garmentfactory.catalog.service.ImageService;
import com.garmentfactory.catalog.service.PublicCatalogService;
import com.garmentfactory.shared.web.PageResponse;
import com.garmentfactory.shared.web.Paging;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The public shop API. No sign-in needed; only published products are ever returned. */
@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog (public)")
public class PublicCatalogController {

    private final PublicCatalogService catalog;
    private final ImageService imageService;

    public PublicCatalogController(PublicCatalogService catalog, ImageService imageService) {
        this.catalog = catalog;
        this.imageService = imageService;
    }

    @GetMapping("/categories")
    public List<PublicCategoryResponse> categories() {
        return catalog.categories();
    }

    @GetMapping("/brands")
    public List<PublicBrandResponse> brands() {
        return catalog.brands();
    }

    /**
     * Sort: newest (default), price_asc, price_desc, name, rating. Several sizes or colours are
     * alternatives (M or L); sizes and colours together must match one and the same variant.
     */
    @GetMapping("/products")
    public PageResponse<ProductCardResponse> products(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String category,
                                                      @RequestParam(required = false) String brand,
                                                      @RequestParam(required = false) BigDecimal minPrice,
                                                      @RequestParam(required = false) BigDecimal maxPrice,
                                                      @RequestParam(required = false) List<String> sizes,
                                                      @RequestParam(required = false) List<String> colors,
                                                      @RequestParam(required = false) Boolean customizable,
                                                      @RequestParam(required = false) String sort,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "24") int size) {
        return catalog.search(q, category, brand, minPrice, maxPrice, sizes, colors, customizable, sort, Paging.of(page, size));
    }

    @GetMapping("/products/{slug}")
    public PublicProductResponse product(@PathVariable String slug) {
        return catalog.detail(slug);
    }

    @GetMapping("/facets")
    public FacetsResponse facets(@RequestParam(required = false) String category) {
        return catalog.facets(category);
    }

    /** Image ids never change content, so browsers and CDNs may cache them for a year. */
    @GetMapping("/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id) {
        ImageService.LoadedImage image = imageService.load(id, true);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}