package com.garmentfactory.cart.api;

/** What the order module may do with a cart. All methods join the caller's transaction. */
public interface CheckoutCartApi {

    /**
     * Locks the user's cart until the transaction ends and returns it priced for the given destination.
     * Throws 422 (CART_EMPTY, CHECKOUT_CART_NOT_READY, COUPON_INVALID, COUPON_USAGE_EXHAUSTED,
     * DELIVERY_NOT_AVAILABLE) when the cart cannot be ordered as it is.
     */
    CheckoutCart lockForCheckout(long userId, String state, String pincode);

    /** Redeems the coupon (limits enforced atomically) and empties the cart. */
    void completeCheckout(long userId, long orderId, CheckoutCart cart);

    /** Gives back the coupon redemption of a cancelled or expired unpaid order. Returns 1 if one was released. */
    int releaseCouponForOrder(long orderId);
}