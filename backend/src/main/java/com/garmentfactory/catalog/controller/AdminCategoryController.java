package com.garmentfactory.catalog.controller;

import com.garmentfactory.catalog.dto.CatalogDtos.BrandRequest;
import com.garmentfactory.catalog.dto.CatalogDtos.BrandResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.CategoryRequest;
import com.garmentfactory.catalog.dto.CatalogDtos.CategoryResponse;
import com.garmentfactory.catalog.service.BrandService;
import com.garmentfactory.catalog.service.CategoryService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff management of categories and brands. Reading needs PRODUCT_VIEW, changing needs CATEGORY_WRITE. */
@RestController
@RequestMapping("/api/v1/admin/catalog")
@Tag(name = "Admin: categories and brands")
public class AdminCategoryController {

    private final CategoryService categories;
    private final BrandService brands;

    public AdminCategoryController(CategoryService categories, BrandService brands) {
        this.categories = categories;
        this.brands = brands;
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<CategoryResponse> listCategories() {
        return categories.list();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CATEGORY_WRITE')")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return categories.create(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_WRITE')")
    public CategoryResponse updateCategory(@PathVariable String id, @Valid @RequestBody CategoryRequest request) {
        return categories.update(id, request);
    }

    @GetMapping("/brands")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<BrandResponse> listBrands() {
        return brands.list();
    }

    @PostMapping("/brands")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CATEGORY_WRITE')")
    public BrandResponse createBrand(@Valid @RequestBody BrandRequest request) {
        return brands.create(request);
    }

    @PutMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_WRITE')")
    public BrandResponse updateBrand(@PathVariable String id, @Valid @RequestBody BrandRequest request) {
        return brands.update(id, request);
    }
}