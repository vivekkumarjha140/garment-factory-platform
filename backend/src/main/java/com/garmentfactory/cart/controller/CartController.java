package com.garmentfactory.cart.controller;

import com.garmentfactory.cart.dto.CartDtos.AddItemRequest;
import com.garmentfactory.cart.dto.CartDtos.ApplyCouponRequest;
import com.garmentfactory.cart.dto.CartDtos.CartResponse;
import com.garmentfactory.cart.dto.CartDtos.UpdateQuantityRequest;
import com.garmentfactory.cart.mapper.CartMapper;
import com.garmentfactory.cart.service.CartService;
import com.garmentfactory.cart.service.CartView;
import com.garmentfactory.cart.service.ShippingDestination;
import com.garmentfactory.customer.api.ShippingAddressLookup;
import com.garmentfactory.customer.api.ShippingTarget;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in customer's own cart. There is no user id in any path, so nobody can reach another cart.
 * Every call accepts an optional addressId (a saved address's public id) for the shipping estimate;
 * without it the customer's default shipping address is used, if there is one.
 */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart")
public class CartController {

    private final CartService service;
    private final ShippingAddressLookup addresses;

    public CartController(CartService service, ShippingAddressLookup addresses) {
        this.service = service;
        this.addresses = addresses;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                            @RequestParam(required = false) String addressId) {
        return respond(user, addressId, d -> service.getCart(user.id(), d));
    }

    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestParam(required = false) String addressId,
                                @Valid @RequestBody AddItemRequest request) {
        return respond(user, addressId, d -> service.addItem(user.id(), request.variantId(), request.quantity(), d));
    }

    @PatchMapping("/items/{itemId}")
    public CartResponse setQuantity(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long itemId,
                                    @RequestParam(required = false) String addressId,
                                    @Valid @RequestBody UpdateQuantityRequest request) {
        return respond(user, addressId, d -> service.setQuantity(user.id(), itemId, request.quantity(), d));
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long itemId,
                                   @RequestParam(required = false) String addressId) {
        return respond(user, addressId, d -> service.removeItem(user.id(), itemId, d));
    }

    @DeleteMapping
    public CartResponse clear(@AuthenticationPrincipal AuthenticatedUser user,
                              @RequestParam(required = false) String addressId) {
        return respond(user, addressId, d -> service.clear(user.id(), d));
    }

    @PostMapping("/coupon")
    public CartResponse applyCoupon(@AuthenticationPrincipal AuthenticatedUser user,
                                    @RequestParam(required = false) String addressId,
                                    @Valid @RequestBody ApplyCouponRequest request) {
        return respond(user, addressId, d -> service.applyCoupon(user.id(), request.code(), d));
    }

    @DeleteMapping("/coupon")
    public CartResponse removeCoupon(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestParam(required = false) String addressId) {
        return respond(user, addressId, d -> service.removeCoupon(user.id(), d));
    }

    private CartResponse respond(AuthenticatedUser user, String addressId,
                                 Function<ShippingDestination, CartView> action) {
        Optional<ShippingTarget> target = resolveTarget(user.id(), addressId);
        ShippingDestination destination = target
                .map(t -> new ShippingDestination(t.state(), t.pincode()))
                .orElse(null);
        return CartMapper.toResponse(action.apply(destination), target.map(ShippingTarget::addressPublicId).orElse(null));
    }

    private Optional<ShippingTarget> resolveTarget(long userId, String addressId) {
        if (addressId == null || addressId.isBlank()) {
            return addresses.findDefaultShipping(userId);
        }
        return Optional.of(addresses.findForUser(userId, addressId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "Address not found.")));
    }
}