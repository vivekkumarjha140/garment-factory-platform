package com.garmentfactory.cart.mapper;

import com.garmentfactory.cart.dto.CartDtos.CartLineResponse;
import com.garmentfactory.cart.dto.CartDtos.CartResponse;
import com.garmentfactory.cart.dto.CartDtos.CouponResponse;
import com.garmentfactory.cart.dto.CartDtos.ShippingResponse;
import com.garmentfactory.cart.dto.CartDtos.TotalsResponse;
import com.garmentfactory.cart.service.CartView;
import java.util.List;

public final class CartMapper {

    private CartMapper() {
    }

    /** shippingAddressId is the address used for the shipping estimate, or null if none. */
    public static CartResponse toResponse(CartView view, String shippingAddressId) {
        List<CartLineResponse> lines = view.lines().stream()
                .map(l -> new CartLineResponse(l.itemId(), l.variantPublicId(), l.productName(), l.productSlug(),
                        l.size(), l.colorName(), l.quantity(), l.unitPrice(), l.lineTotal(),
                        l.gstRate(), l.gstAmount(), l.availableStock(),
                        l.issue() == null ? null : l.issue().name()))
                .toList();

        CouponResponse coupon = view.coupon() == null ? null
                : new CouponResponse(view.coupon().code(), view.coupon().description(),
                view.coupon().type().name(), view.coupon().discount(), view.coupon().freeShipping());

        ShippingResponse shipping = view.shipping() == null ? null
                : new ShippingResponse(view.shipping().zoneName(), view.shipping().freeShippingApplied(),
                view.shipping().estimatedDaysMin(), view.shipping().estimatedDaysMax());

        CartView.Totals t = view.totals();
        TotalsResponse totals = new TotalsResponse(t.totalQuantity(), t.subtotal(), t.discount(), t.taxableAmount(),
                t.gstAmount(), t.shippingCharge(), t.shippingCalculated(), t.total());

        return new CartResponse(lines, coupon, view.couponError(), shipping, view.shippingError(), totals,
                view.checkoutReady(), shippingAddressId);
    }
}