package com.garmentfactory.customer.api;

/** Just what other modules need from an address to price shipping. */
public record ShippingTarget(String addressPublicId, String state, String pincode) {
}