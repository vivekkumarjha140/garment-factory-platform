package com.garmentfactory.inventory.service;

import java.math.BigDecimal;

public record FinishedGoodsReceiptCommand(
        Long variantId,
        int quantity,
        BigDecimal unitCost,
        String supplierName,
        String supplierReference,
        String notes) { }
