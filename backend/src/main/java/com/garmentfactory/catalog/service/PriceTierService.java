package com.garmentfactory.catalog.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.catalog.dto.CatalogDtos.PriceTierResponse;
import com.garmentfactory.catalog.dto.CatalogDtos.PriceTiersRequest;
import com.garmentfactory.catalog.entity.Product;
import com.garmentfactory.catalog.entity.ProductStatus;
import com.garmentfactory.catalog.entity.QuantityPriceTier;
import com.garmentfactory.catalog.repository.ProductRepository;
import com.garmentfactory.catalog.repository.QuantityPriceTierRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quantity-based unit prices for bulk buying. The whole list is replaced in one step. */
@Service
public class PriceTierService {

    private final ProductRepository products;
    private final QuantityPriceTierRepository tiers;
    private final AuditRecorder audit;

    public PriceTierService(ProductRepository products, QuantityPriceTierRepository tiers, AuditRecorder audit) {
        this.products = products;
        this.tiers = tiers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PriceTierResponse> get(String productPublicId) {
        Product product = find(productPublicId);
        return tiers.findByProductIdOrderByMinQtyAsc(product.getId()).stream().map(CatalogMapper::toResponse).toList();
    }

    @Transactional
    public List<PriceTierResponse> replace(String productPublicId, PriceTiersRequest request) {
        Product product = find(productPublicId);
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw ApiException.conflict("PRODUCT_ARCHIVED", "An archived product cannot be changed. Restore it to draft first.");
        }
        List<PriceTiers.Tier> wanted = request.tiers().stream()
                .map(t -> new PriceTiers.Tier(t.minQty(), t.maxQty(), t.unitPrice().setScale(2)))
                .toList();
        PriceTiers.validate(wanted, product.getBasePrice());

        String before = describe(tiers.findByProductIdOrderByMinQtyAsc(product.getId()).stream()
                .map(t -> new PriceTiers.Tier(t.getMinQty(), t.getMaxQty(), t.getUnitPrice())).toList());

        // Delete first and flush, so the (product, min quantity) unique key never sees old and new rows together.
        tiers.deleteByProductId(product.getId());
        tiers.flush();
        for (PriceTiers.Tier tier : wanted) {
            QuantityPriceTier row = new QuantityPriceTier();
            row.setProduct(product);
            row.setMinQty(tier.minQty());
            row.setMaxQty(tier.maxQty());
            row.setUnitPrice(tier.unitPrice());
            tiers.save(row);
        }

        audit.record(AuditEvent.of("PRICE_TIERS_REPLACED", "PRODUCT", product.getPublicId(),
                "from [" + before + "] to [" + describe(wanted) + "]"));
        return wanted.stream().map(t -> new PriceTierResponse(t.minQty(), t.maxQty(), t.unitPrice())).toList();
    }

    private Product find(String publicId) {
        return products.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found."));
    }

    private static String describe(List<PriceTiers.Tier> list) {
        return list.stream()
                .map(t -> t.minQty() + (t.maxQty() == null ? "+" : "-" + t.maxQty()) + "@" + t.unitPrice().toPlainString())
                .collect(Collectors.joining("; "));
    }
}