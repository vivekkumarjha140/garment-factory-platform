package com.garmentfactory.inventory.service;

import com.garmentfactory.catalog.api.VariantLookup;
import com.garmentfactory.catalog.api.VariantSummary;
import com.garmentfactory.inventory.entity.FinishedGoodsStock;
import com.garmentfactory.inventory.exception.StockNotFoundException;
import com.garmentfactory.inventory.repository.FinishedGoodsStockRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stock rows joined with catalog variant details (through the catalog module's public API). */
@Service
@Transactional(readOnly = true)
public class StockOverviewService {

    /** The variant is null if the catalog no longer reports it. */
    public record StockOverview(FinishedGoodsStock stock, VariantSummary variant) { }

    private static final int SEARCH_LIMIT = 100;

    private final FinishedGoodsStockRepository stockRepository;
    private final VariantLookup variantLookup;

    public StockOverviewService(FinishedGoodsStockRepository stockRepository, VariantLookup variantLookup) {
        this.stockRepository = stockRepository;
        this.variantLookup = variantLookup;
    }

    public Page<StockOverview> list(String query, boolean lowStockOnly, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
        if (query == null || query.isBlank()) {
            Page<FinishedGoodsStock> page = lowStockOnly
                    ? stockRepository.findLowStock(sorted)
                    : stockRepository.findAll(sorted);
            Map<Long, VariantSummary> variants = variantLookup.findByIds(
                    page.getContent().stream().map(FinishedGoodsStock::getVariantId).toList());
            return page.map(stock -> new StockOverview(stock, variants.get(stock.getVariantId())));
        }

        // Text search: find matching variants in the catalog first, then their stock rows.
        Map<Long, VariantSummary> matches = new LinkedHashMap<>();
        variantLookup.search(query, SEARCH_LIMIT).forEach(variant -> matches.put(variant.id(), variant));
        List<StockOverview> rows = matches.isEmpty() ? List.of() : stockRepository.findByVariantIdIn(matches.keySet()).stream()
                .filter(stock -> !lowStockOnly || stock.isLowStock())
                .sorted(Comparator.comparing(FinishedGoodsStock::getId))
                .map(stock -> new StockOverview(stock, matches.get(stock.getVariantId())))
                .toList();
        int from = (int) Math.min((long) sorted.getPageNumber() * sorted.getPageSize(), rows.size());
        int to = Math.min(from + sorted.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(from, to), sorted, rows.size());
    }

    public StockOverview get(long variantId) {
        FinishedGoodsStock stock = stockRepository.findByVariantId(variantId)
                .orElseThrow(() -> new StockNotFoundException("No stock record for variant " + variantId));
        return new StockOverview(stock, variantLookup.findById(variantId).orElse(null));
    }
}