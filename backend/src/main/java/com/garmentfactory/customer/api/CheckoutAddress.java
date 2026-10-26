package com.garmentfactory.customer.api;

/** A saved address in full, for copying into an order snapshot. */
public record CheckoutAddress(String addressPublicId, String recipientName, String phone, String line1,
                              String line2, String city, String state, String pincode) {
}