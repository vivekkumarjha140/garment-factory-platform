package com.garmentfactory.inventory.service;

import com.garmentfactory.inventory.api.InventoryApi;
import com.garmentfactory.inventory.api.StockAvailability;
import com.garmentfactory.inventory.api.StockLine;
import com.garmentfactory.inventory.entity.FinishedGoodsStock;
import com.garmentfactory.inventory.repository.FinishedGoodsStockRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryApiService implements InventoryApi {

    private final FinishedGoodsStockService stockService;
    private final FinishedGoodsStockRepository stockRepository;

    public InventoryApiService(FinishedGoodsStockService stockService, FinishedGoodsStockRepository stockRepository) {
        this.stockService = stockService;
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional
    public void reserveForOrder(long orderId, List<StockLine> lines, Long actorUserId) {
        List<ReservationLine> mapped = lines.stream()
                .map(line -> new ReservationLine(line.variantId(), line.quantity()))
                .toList();
        stockService.reserveAll(orderId, mapped, actorUserId);
    }

    @Override
    @Transactional
    public int releaseForOrder(long orderId, String reason, Long actorUserId) {
        return stockService.releaseForOrder(orderId, reason, actorUserId);
    }

    @Override
    @Transactional
    public int consumeForOrder(long orderId, Long actorUserId) {
        return stockService.consumeForOrder(orderId, actorUserId);
    }

    @Override
    @Transactional
    public int restockForOrder(long orderId, String reason, Long actorUserId) {
        return stockService.restockForOrder(orderId, reason, actorUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockAvailability> availability(long variantId) {
        return stockRepository.findByVariantId(variantId).map(InventoryApiService::toAvailability);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, StockAvailability> availability(Collection<Long> variantIds) {
        Map<Long, StockAvailability> result = new LinkedHashMap<>();
        if (variantIds == null || variantIds.isEmpty()) {
            return result;
        }
        stockRepository.findByVariantIdIn(variantIds)
                .forEach(stock -> result.put(stock.getVariantId(), toAvailability(stock)));
        return result;
    }

    private static StockAvailability toAvailability(FinishedGoodsStock stock) {
        return new StockAvailability(stock.getVariantId(), stock.getOnHand(), stock.getReserved(), stock.getAvailable());
    }
}