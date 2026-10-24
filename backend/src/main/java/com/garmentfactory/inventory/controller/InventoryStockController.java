package com.garmentfactory.inventory.controller;

import static com.garmentfactory.inventory.dto.InventoryDtos.*;
import static com.garmentfactory.inventory.dto.StockOverviewDtos.*;

import com.garmentfactory.inventory.config.InventoryActorResolver;
import com.garmentfactory.inventory.service.FinishedGoodsReceiptCommand;
import com.garmentfactory.inventory.service.FinishedGoodsStockService;
import com.garmentfactory.inventory.service.StockInitializationService;
import com.garmentfactory.inventory.service.StockOverviewService;
import com.garmentfactory.inventory.service.StockQueryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryStockController {

    private static final int DEFAULT_THRESHOLD = 10;

    private final FinishedGoodsStockService stockService;
    private final StockOverviewService overviewService;
    private final StockInitializationService initializationService;
    private final StockQueryService queryService;
    private final InventoryActorResolver actor;

    public InventoryStockController(FinishedGoodsStockService stockService, StockOverviewService overviewService,
                                    StockInitializationService initializationService, StockQueryService queryService,
                                    InventoryActorResolver actor) {
        this.stockService = stockService;
        this.overviewService = overviewService;
        this.initializationService = initializationService;
        this.queryService = queryService;
        this.actor = actor;
    }

    // ------------------------------------------------------------------ reads (STOCK_VIEW)

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<StockItemResponse> list(@RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "false") boolean lowStockOnly,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(overviewService.list(q, lowStockOnly, PageResponse.pageable(page, size)),
                overview -> StockItemResponse.from(overview.stock(), overview.variant()));
    }

    @GetMapping("/stock/{variantId}")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public StockItemResponse get(@PathVariable Long variantId) {
        var overview = overviewService.get(variantId);
        return StockItemResponse.from(overview.stock(), overview.variant());
    }

    @GetMapping("/stock/{variantId}/movements")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<MovementResponse> movements(@PathVariable Long variantId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(queryService.movementsForVariant(variantId, PageResponse.pageable(page, size)),
                MovementResponse::from);
    }

    @GetMapping("/receipts")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<ReceiptResponse> receipts(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(queryService.receipts(PageResponse.pageable(page, size)), ReceiptResponse::from);
    }

    // ------------------------------------------------------------------ initialization (STOCK_ADJUST)

    /** Catalog variants that have no stock row yet (picker for "add to inventory"). */
    @GetMapping("/stock/uninitialized")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public List<UninitializedVariantResponse> uninitialized(@RequestParam(required = false) String q,
                                                            @RequestParam(defaultValue = "20") int limit) {
        return initializationService.findUninitialized(q, limit).stream()
                .map(UninitializedVariantResponse::from).toList();
    }

    @PostMapping("/stock/initialize-missing")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public InitializeMissingResponse initializeMissing(@Valid @RequestBody(required = false) InitializeMissingRequest req) {
        int threshold = req == null || req.lowStockThreshold() == null ? DEFAULT_THRESHOLD : req.lowStockThreshold();
        return new InitializeMissingResponse(initializationService.initializeMissing(threshold));
    }

    @PostMapping("/stock")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockResponse initialize(@Valid @RequestBody InitializeStockRequest req) {
        int threshold = req.lowStockThreshold() == null ? DEFAULT_THRESHOLD : req.lowStockThreshold();
        return StockResponse.from(initializationService.initialize(req.variantId(), threshold));
    }

    // ------------------------------------------------------------------ writes (STOCK_ADJUST)

    @PatchMapping("/stock/{variantId}/threshold")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockResponse updateThreshold(@PathVariable Long variantId, @Valid @RequestBody ThresholdRequest req) {
        return StockResponse.from(stockService.updateLowStockThreshold(variantId, req.lowStockThreshold()));
    }

    @PostMapping("/stock/{variantId}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockResponse receive(@PathVariable Long variantId, @Valid @RequestBody ReceiveStockRequest req) {
        var cmd = new FinishedGoodsReceiptCommand(variantId, req.quantity(), req.unitCost(),
                req.supplierName(), req.supplierReference(), req.notes());
        return StockResponse.from(stockService.receive(cmd, actor.currentUserId()));
    }

    @PostMapping("/stock/{variantId}/adjustments")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockResponse adjust(@PathVariable Long variantId, @Valid @RequestBody AdjustStockRequest req) {
        return StockResponse.from(stockService.adjust(variantId, req.delta(), req.reason(), actor.currentUserId()));
    }

    @PostMapping("/stock/{variantId}/damage")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public StockResponse damage(@PathVariable Long variantId, @Valid @RequestBody DamageStockRequest req) {
        return StockResponse.from(stockService.markDamaged(variantId, req.quantity(), req.reason(), actor.currentUserId()));
    }
}