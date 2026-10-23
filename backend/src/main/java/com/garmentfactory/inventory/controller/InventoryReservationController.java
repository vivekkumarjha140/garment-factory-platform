
package com.garmentfactory.inventory.controller;

import com.garmentfactory.inventory.config.InventoryActorResolver;
import com.garmentfactory.inventory.dto.InventoryDtos.ReleaseResponse;
import com.garmentfactory.inventory.dto.InventoryDtos.ReservationResponse;
import com.garmentfactory.inventory.dto.InventoryDtos.ReserveRequest;
import com.garmentfactory.inventory.service.FinishedGoodsStockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/reservations")
public class InventoryReservationController {

    private final FinishedGoodsStockService stockService;
    private final InventoryActorResolver actor;

    public InventoryReservationController(
            FinishedGoodsStockService stockService,
            InventoryActorResolver actor) {
        this.stockService = stockService;
        this.actor = actor;
    }

    /** Returns 409 Conflict when available stock is insufficient. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public ReservationResponse reserve(
            @Valid @RequestBody ReserveRequest req) {
        return ReservationResponse.from(
                stockService.reserve(
                        req.orderId(),
                        req.variantId(),
                        req.quantity(),
                        actor.currentUserId()));
    }

    @DeleteMapping("/orders/{orderId}")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public ReleaseResponse releaseForOrder(@PathVariable Long orderId) {
        int released = stockService.releaseForOrder(
                orderId, "Released manually", actor.currentUserId());

        return new ReleaseResponse(orderId, released);
    }
}
