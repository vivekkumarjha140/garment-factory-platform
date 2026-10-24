package com.garmentfactory.catalog.api;

/** Published by the catalog module after a variant is created. Other modules may react to it. */
public record VariantCreated(long variantId, String publicId, String sku) { }