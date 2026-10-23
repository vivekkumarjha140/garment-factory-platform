package com.garmentfactory.catalog.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.catalog.dto.CatalogDtos.BrandRequest;
import com.garmentfactory.catalog.dto.CatalogDtos.BrandResponse;
import com.garmentfactory.catalog.entity.Brand;
import com.garmentfactory.catalog.repository.BrandRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandService {

    private final BrandRepository brands;
    private final AuditRecorder audit;

    public BrandService(BrandRepository brands, AuditRecorder audit) {
        this.brands = brands;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> list() {
        return brands.findAllByOrderByNameAsc().stream().map(CatalogMapper::toResponse).toList();
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        String name = request.name().trim();
        requireFreeName(name, 0L);
        Brand brand = new Brand();
        brand.setName(name);
        brand.setActive(request.active() == null || request.active());
        brands.save(brand);
        audit.record(AuditEvent.of("BRAND_CREATED", "BRAND", brand.getPublicId(), "name=" + name));
        return CatalogMapper.toResponse(brand);
    }

    @Transactional
    public BrandResponse update(String publicId, BrandRequest request) {
        Brand brand = brands.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "Brand not found."));
        String name = request.name().trim();
        requireFreeName(name, brand.getId());
        brand.setName(name);
        if (request.active() != null) {
            brand.setActive(request.active());
        }
        audit.record(AuditEvent.of("BRAND_UPDATED", "BRAND", brand.getPublicId(),
                "name=" + name + ", active=" + brand.isActive()));
        return CatalogMapper.toResponse(brand);
    }

    private void requireFreeName(String name, Long ownId) {
        if (brands.existsByNameAndIdNot(name, ownId)) {
            throw ApiException.conflict("BRAND_ALREADY_EXISTS", "A brand with this name already exists.");
        }
    }
}