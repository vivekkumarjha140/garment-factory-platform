package com.garmentfactory.inventory.service;

import com.garmentfactory.catalog.api.VariantLookup;
import com.garmentfactory.catalog.api.VariantSummary;
import com.garmentfactory.inventory.entity.InventoryAuditLine;
import com.garmentfactory.inventory.entity.RawMaterial;
import com.garmentfactory.inventory.entity.StockItemType;
import com.garmentfactory.inventory.repository.RawMaterialRepository;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Human-readable names for audit lines (variant from the catalog, raw material from the master list). */
@Service
@Transactional(readOnly = true)
public class AuditLineLabelService {

    public record ItemLabel(String name, String sku) { }

    private final VariantLookup variantLookup;
    private final RawMaterialRepository rawMaterialRepository;

    public AuditLineLabelService(VariantLookup variantLookup, RawMaterialRepository rawMaterialRepository) {
        this.variantLookup = variantLookup;
        this.rawMaterialRepository = rawMaterialRepository;
    }

    /** Label per audit line id. Items that no longer resolve get a "#id" fallback name. */
    public Map<Long, ItemLabel> labelsByLineId(List<InventoryAuditLine> lines) {
        Set<Long> variantIds = new LinkedHashSet<>();
        Set<Long> rawIds = new LinkedHashSet<>();
        for (InventoryAuditLine line : lines) {
            if (line.getItemType() == StockItemType.FINISHED_GOOD) {
                variantIds.add(line.getVariantId());
            } else {
                rawIds.add(line.getRawMaterialId());
            }
        }
        Map<Long, VariantSummary> variants = variantIds.isEmpty() ? Map.of() : variantLookup.findByIds(variantIds);
        Map<Long, RawMaterial> materials = new HashMap<>();
        if (!rawIds.isEmpty()) {
            rawMaterialRepository.findAllById(rawIds).forEach(material -> materials.put(material.getId(), material));
        }

        Map<Long, ItemLabel> labels = new HashMap<>();
        for (InventoryAuditLine line : lines) {
            labels.put(line.getId(), line.getItemType() == StockItemType.FINISHED_GOOD
                    ? variantLabel(line.getVariantId(), variants.get(line.getVariantId()))
                    : rawLabel(line.getRawMaterialId(), materials.get(line.getRawMaterialId())));
        }
        return labels;
    }

    private static ItemLabel variantLabel(Long id, VariantSummary variant) {
        if (variant == null) {
            return new ItemLabel("Variant #" + id, null);
        }
        return new ItemLabel(variant.productName() + " (" + variant.size() + " / " + variant.colorName() + ")", variant.sku());
    }

    private static ItemLabel rawLabel(Long id, RawMaterial material) {
        return material == null
                ? new ItemLabel("Raw material #" + id, null)
                : new ItemLabel(material.getName(), material.getCode());
    }
}