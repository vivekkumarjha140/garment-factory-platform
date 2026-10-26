package com.garmentfactory.inventory.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The inventory module's public API for other modules (orders, shipping). Other modules never touch
 * inventory entities, repositories or services. Methods join the caller's transaction, so a failed
 * reservation rolls the caller's own work back too. actorUserId may be null for system actions.
 */
public interface InventoryApi {

    /** All-or-nothing. Throws 409 INSUFFICIENT_STOCK if any line cannot be covered. */
    void reserveForOrder(long orderId, List<StockLine> lines, Long actorUserId);

    /** Releases every ACTIVE reservation of the order. Returns how many were released. */
    int releaseForOrder(long orderId, String reason, Long actorUserId);

    /** Turns the order's ACTIVE reservations into real deductions. Returns how many were consumed. */
    int consumeForOrder(long orderId, Long actorUserId);

    /** Puts the stock of a cancelled, already-deducted order back. Idempotent. Returns how many lines were returned. */
    int restockForOrder(long orderId, String reason, Long actorUserId);

    Optional<StockAvailability> availability(long variantId);

    Map<Long, StockAvailability> availability(Collection<Long> variantIds);
}