package com.garmentfactory.inventory.service;

import java.math.BigDecimal;

public record RawMaterialReceiptCommand(
        Long rawMaterialId,
        BigDecimal quantity,
        BigDecimal unitCost,
        String supplierName,
        String supplierReference,
        String notes) { }
