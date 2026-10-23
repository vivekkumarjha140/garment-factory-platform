
package com.garmentfactory.inventory.controller;

import com.garmentfactory.inventory.config.InventoryActorResolver;
import com.garmentfactory.inventory.entity.RawMaterialCategory;
import com.garmentfactory.inventory.service.RawMaterialReceiptCommand;
import com.garmentfactory.inventory.service.RawMaterialStockService;
import com.garmentfactory.inventory.service.StockQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.garmentfactory.inventory.dto.InventoryDtos.*;

@RestController
@RequestMapping("/api/v1/inventory/raw-materials")
public class InventoryRawMaterialController {

    private final RawMaterialStockService rawService;
    private final StockQueryService queryService;
    private final InventoryActorResolver actor;

    public InventoryRawMaterialController(
            RawMaterialStockService rawService,
            StockQueryService queryService,
            InventoryActorResolver actor) {
        this.rawService = rawService;
        this.queryService = queryService;
        this.actor = actor;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<RawMaterialResponse> list(
            @RequestParam(required = false) RawMaterialCategory category,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                queryService.listRawMaterials(
                        category, lowStockOnly, PageResponse.pageable(page, size)),
                RawMaterialResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public RawMaterialResponse get(@PathVariable Long id) {
        return RawMaterialResponse.from(rawService.get(id));
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize("hasAuthority('STOCK_VIEW')")
    public PageResponse<MovementResponse> movements(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                queryService.movementsForRawMaterial(
                        id, PageResponse.pageable(page, size)),
                MovementResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public RawMaterialResponse create(
            @Valid @RequestBody CreateRawMaterialRequest req) {
        return RawMaterialResponse.from(
                rawService.create(
                        req.code(), req.name(), req.category(),
                        req.uom(), req.lowStockThreshold(), req.unitCost()));
    }

    @PostMapping("/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public RawMaterialResponse receive(
            @PathVariable Long id,
            @Valid @RequestBody ReceiveRawMaterialRequest req) {
        var cmd = new RawMaterialReceiptCommand(
                id, req.quantity(), req.unitCost(),
                req.supplierName(), req.supplierReference(), req.notes());

        return RawMaterialResponse.from(
                rawService.receive(cmd, actor.currentUserId()));
    }

    @PostMapping("/{id}/adjustments")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public RawMaterialResponse adjust(
            @PathVariable Long id,
            @Valid @RequestBody AdjustRawMaterialRequest req) {
        return RawMaterialResponse.from(
                rawService.adjust(
                        id, req.delta(), req.reason(), actor.currentUserId()));
    }
}
