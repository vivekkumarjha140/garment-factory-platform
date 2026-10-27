package com.garmentfactory.order.controller;

import com.garmentfactory.order.dto.OrderDtos.CancelOrderRequest;
import com.garmentfactory.order.dto.OrderDtos.OrderResponse;
import com.garmentfactory.order.dto.OrderDtos.PlaceOrderRequest;
import com.garmentfactory.order.mapper.OrderMapper;
import com.garmentfactory.order.service.OrderCancellationService;
import com.garmentfactory.order.service.OrderCreationService;
import com.garmentfactory.order.service.OrderQueryService;
import com.garmentfactory.order.service.PlaceOrderCommand;
import com.garmentfactory.order.service.PlacedOrder;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.garmentfactory.order.dto.OrderDtos.OrderSummaryResponse;
import com.garmentfactory.shared.web.PageResponse;

/** The signed-in customer's own orders. There is no user id in any path. */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final OrderCreationService creation;
    private final OrderQueryService queries;
    private final OrderCancellationService cancellation;

    public OrderController(OrderCreationService creation, OrderQueryService queries, OrderCancellationService cancellation) {
        this.creation = creation;
        this.queries = queries;
        this.cancellation = cancellation;
    }

    /** 201 for a new order, 200 with Idempotent-Replayed: true when the same Idempotency-Key was already used. */
    @PostMapping
    public ResponseEntity<OrderResponse> place(@AuthenticationPrincipal AuthenticatedUser user,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                               @Valid @RequestBody PlaceOrderRequest request) {
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "The Idempotency-Key header is required.");
        }
        if (!KEY.matcher(key).matches()) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key must be 8-64 characters: letters, digits, '-' or '_'.");
        }
        PlacedOrder placed = creation.place(user.id(),
                new PlaceOrderCommand(key, request.shippingAddressId().trim(),
                        request.billingAddressId() == null ? null : request.billingAddressId().trim(),
                        request.paymentMethod() == null ? "ONLINE" : request.paymentMethod()));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(placed.replayed() ? HttpStatus.OK : HttpStatus.CREATED);
        if (placed.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(OrderMapper.toResponse(placed.details()));
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderNumber) {
        return OrderMapper.toResponse(queries.getForUser(user.id(), orderNumber));
    }

    /** The signed-in customer's order history, newest first. */
    @GetMapping
    public PageResponse<OrderSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return PageResponse.from(queries.listForUser(user.id(), page, size).map(OrderMapper::toSummary));
    }

    /** Cancels the customer's own order while that is still possible. Repeating it is harmless (200). */
    @PostMapping("/{orderNumber}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderNumber,
                                @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return OrderMapper.toResponse(cancellation.cancelByCustomer(user.id(), orderNumber,
                request == null ? null : request.reason()));
    }
}