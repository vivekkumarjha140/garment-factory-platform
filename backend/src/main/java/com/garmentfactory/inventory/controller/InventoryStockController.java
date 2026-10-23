
package com.garmentfactory.inventory.controller;

import com.garmentfactory.inventory.config.InventoryActorResolver;
import com.garmentfactory.inventory.service.FinishedGoodsStockService;
import com.garmentfactory.inventory.service.StockQueryService;
import com.garmentfactory.inventory.service.FinishedGoodsReceiptCommand;
import com.garmentfactory.inventory.dto.InventoryDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.garmentfactory.inventory.dto.InventoryDtos.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryStockController {

    private final FinishedGoodsStockService stockService;
    private final StockQueryService queryService;
    private final InventoryActorResolver actor;

    public InventoryStockController(
            FinishedGoodsStockService stockService,
            StockQueryService queryService,
            InventoryActorResolver actor) {
        this.stockService = stockService;
        this.queryService = queryService;
        this.actor = actor;
    }

    // Reads: STOCK_VIEW

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<StockResponse> list(
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                queryService.listStock(lowStockOnly, PageResponse.pageable(page, size)),
                StockResponse::from);
    }

    @GetMapping("/stock/{variantId}")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public StockResponse get(@PathVariable Long variantId) {
        return StockResponse.from(stockService.getStock(variantId));
    }

    @GetMapping("/stock/{variantId}/movements")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<MovementResponse> movements(
            @PathVariable Long variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                queryService.movementsForVariant(
                        variantId, PageResponse.pageable(page, size)),
                MovementResponse::from);
    }

    @GetMapping("/receipts")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<ReceiptResponse> receipts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                queryService.receipts(PageResponse.pageable(page, size)),
                ReceiptResponse::from);
    }

    // Writes: STOCK_ADJUST

    @PostMapping("/stock")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockResponse initialize(
            @Valid @RequestBody InitializeStockRequest req) {
        int threshold = req.lowStockThreshold() == null
                ? 10 : req.lowStockThreshold();
        return StockResponse.from(
                stockService.initializeStock(req.variantId(), threshold));
    }

    @PatchMapping("/stock/{variantId}/threshold")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockResponse updateThreshold(
            @PathVariable Long variantId,
            @Valid @RequestBody ThresholdRequest req) {
        return StockResponse.from(
                stockService.updateLowStockThreshold(
                        variantId, req.lowStockThreshold()));
    }

    @PostMapping("/stock/{variantId}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockResponse receive(
            @PathVariable Long variantId,
            @Valid @RequestBody ReceiveStockRequest req) {
        var cmd = new FinishedGoodsReceiptCommand(
                variantId, req.quantity(), req.unitCost(),
                req.supplierName(), req.supplierReference(), req.notes());
        return StockResponse.from(
                stockService.receive(cmd, actor.currentUserId()));
    }

    @PostMapping("/stock/{variantId}/adjustments")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockResponse adjust(
            @PathVariable Long variantId,
            @Valid @RequestBody AdjustStockRequest req) {
        return StockResponse.from(
                stockService.adjust(
                        variantId, req.delta(), req.reason(),
                        actor.currentUserId()));
    }

    @PostMapping("/stock/{variantId}/damage")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockResponse damage(
            @PathVariable Long variantId,
            @Valid @RequestBody DamageStockRequest req) {
        return StockResponse.from(
                stockService.markDamaged(
                        variantId, req.quantity(), req.reason(),
                        actor.currentUserId()));
    }
}

