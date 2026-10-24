package com.garmentfactory.catalog.api;

/** Read-only view of a variant for other modules. Other modules never touch catalog entities. */
public record VariantSummary(long id, String publicId, String sku, String size, String colorName, String status,
                             String productPublicId, String productName) { }