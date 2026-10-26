package com.garmentfactory.cart.service;

/** Where the parcel will go. Optional while browsing the cart; required for a shipping charge. */
public record ShippingDestination(String state, String pincode) {
}