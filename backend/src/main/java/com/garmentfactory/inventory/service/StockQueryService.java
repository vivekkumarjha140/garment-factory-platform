package com.garmentfactory.inventory.service;

import com.garmentfactory.inventory.entity.*;
import com.garmentfactory.inventory.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockQueryService {

    private final FinishedGoodsStockRepository stockRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final StockMovementRepository movementRepository;
    private final StockReceiptRepository receiptRepository;

    public StockQueryService(FinishedGoodsStockRepository stockRepository,
                             RawMaterialRepository rawMaterialRepository,
                             StockMovementRepository movementRepository,
                             StockReceiptRepository receiptRepository) {
        this.stockRepository = stockRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.movementRepository = movementRepository;
        this.receiptRepository = receiptRepository;
    }

    public Page<FinishedGoodsStock> listStock(boolean lowStockOnly, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
        return lowStockOnly ? stockRepository.findLowStock(sorted) : stockRepository.findAll(sorted);
    }

    public Page<RawMaterial> listRawMaterials(RawMaterialCategory category, boolean lowStockOnly, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
        if (lowStockOnly) return rawMaterialRepository.findLowStock(sorted);
        if (category != null) return rawMaterialRepository.findByCategoryAndActiveTrue(category, sorted);
        return rawMaterialRepository.findAll(sorted);
    }

    public Page<StockMovement> movementsForVariant(Long variantId, Pageable pageable) {
        return movementRepository.findByVariantIdOrderByCreatedAtDescIdDesc(variantId, pageable);
    }

    public Page<StockMovement> movementsForRawMaterial(Long rawMaterialId, Pageable pageable) {
        return movementRepository.findByRawMaterialIdOrderByCreatedAtDescIdDesc(rawMaterialId, pageable);
    }

    public Page<StockReceipt> receipts(Pageable pageable) {
        return receiptRepository.findAllByOrderByReceivedAtDescIdDesc(pageable);
    }
}