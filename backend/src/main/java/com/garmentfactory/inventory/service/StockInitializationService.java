package com.garmentfactory.inventory.service;

import com.garmentfactory.catalog.api.VariantLookup;
import com.garmentfactory.catalog.api.VariantSummary;
import com.garmentfactory.inventory.entity.FinishedGoodsStock;
import com.garmentfactory.inventory.exception.InvalidStockOperationException;
import com.garmentfactory.inventory.repository.FinishedGoodsStockRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Gets catalog variants into inventory. Deliberately not @Transactional itself: every stock row is
 * created in its own short transaction, so one concurrent duplicate cannot poison a whole bulk run.
 */
@Service
public class StockInitializationService {

    private static final int BATCH_SIZE = 500;
    private static final int PICKER_SEARCH_LIMIT = 100;

    private final VariantLookup variantLookup;
    private final FinishedGoodsStockRepository stockRepository;
    private final FinishedGoodsStockService stockService;

    public StockInitializationService(VariantLookup variantLookup, FinishedGoodsStockRepository stockRepository,
                                      FinishedGoodsStockService stockService) {
        this.variantLookup = variantLookup;
        this.stockRepository = stockRepository;
        this.stockService = stockService;
    }

    /** Creates the stock row for one variant (idempotent). 404 VARIANT_NOT_FOUND if the catalog has no such variant. */
    public FinishedGoodsStock initialize(Long variantId, int lowStockThreshold) {
        variantLookup.findById(variantId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variant not found: " + variantId));
        return stockService.initializeStock(variantId, lowStockThreshold);
    }

    /** Catalog variants matching the text that still have no stock row (for the "add to inventory" picker). */
    public List<VariantSummary> findUninitialized(String query, int limit) {
        List<VariantSummary> candidates = variantLookup.search(query, PICKER_SEARCH_LIMIT);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> existing = stockRepository.findByVariantIdIn(candidates.stream().map(VariantSummary::id).toList())
                .stream().map(FinishedGoodsStock::getVariantId).collect(Collectors.toSet());
        return candidates.stream().filter(variant -> !existing.contains(variant.id())).limit(Math.max(limit, 1)).toList();
    }

    /** Creates stock rows for every non-archived variant that has none. Returns how many were created. */
    public int initializeMissing(int lowStockThreshold) {
        if (lowStockThreshold < 0) {
            throw new InvalidStockOperationException("Threshold cannot be negative");
        }
        long afterId = 0;
        int created = 0;
        while (true) {
            List<Long> ids = variantLookup.variantIdsAfter(afterId, BATCH_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            Set<Long> existing = stockRepository.findByVariantIdIn(ids).stream()
                    .map(FinishedGoodsStock::getVariantId).collect(Collectors.toSet());
            for (Long id : ids) {
                if (existing.contains(id)) {
                    continue;
                }
                try {
                    stockService.initializeStock(id, lowStockThreshold);
                    created++;
                } catch (DataIntegrityViolationException ex) {
                    // Someone created the row at the same moment; nothing to do.
                }
            }
            afterId = ids.get(ids.size() - 1);
        }
        return created;
    }
}