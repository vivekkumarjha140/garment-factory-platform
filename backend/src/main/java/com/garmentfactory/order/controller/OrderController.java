package com.garmentfactory.order.controller;

import com.garmentfactory.order.dto.OrderDtos.OrderResponse;
import com.garmentfactory.order.dto.OrderDtos.PlaceOrderRequest;
import com.garmentfactory.order.mapper.OrderMapper;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer's own orders. There is no user id in any path. */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final OrderCreationService creation;
    private final OrderQueryService queries;

    public OrderController(OrderCreationService creation, OrderQueryService queries) {
        this.creation = creation;
        this.queries = queries;
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
                        request.billingAddressId() == null ? null : request.billingAddressId().trim()));
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
}