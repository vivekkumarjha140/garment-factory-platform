package com.garmentfactory.inventory.service;

import com.garmentfactory.catalog.api.VariantCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** Gives every new catalog variant a stock row, so it shows up on the inventory dashboard straight away. */
@Component
class VariantCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(VariantCreatedListener.class);

    private final FinishedGoodsStockService stockService;
    private final TransactionTemplate requiresNew;
    private final int defaultThreshold;

    VariantCreatedListener(FinishedGoodsStockService stockService, PlatformTransactionManager transactionManager,
                           @Value("${inventory.default-low-stock-threshold:10}") int defaultThreshold) {
        this.stockService = stockService;
        this.defaultThreshold = defaultThreshold;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onVariantCreated(VariantCreated event) {
        try {
            requiresNew.executeWithoutResult(status -> stockService.initializeStock(event.variantId(), defaultThreshold));
        } catch (RuntimeException ex) {
            log.warn("Could not create the stock row for variant {}; 'initialize missing' will cover it", event.variantId(), ex);
        }
    }
}