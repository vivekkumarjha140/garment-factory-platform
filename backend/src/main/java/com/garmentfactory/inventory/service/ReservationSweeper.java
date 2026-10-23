package com.garmentfactory.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "inventory.sweeper",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationSweeper {

    private static final Logger log =
            LoggerFactory.getLogger(ReservationSweeper.class);

    private final ReservationExpiryService expiryService;
    private final int batchSize;
    private final int maxBatchesPerRun;

    public ReservationSweeper(
            ReservationExpiryService expiryService,
            @Value("${inventory.sweeper.batch-size:100}") int batchSize,
            @Value("${inventory.sweeper.max-batches-per-run:20}") int maxBatchesPerRun) {

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "inventory.sweeper.batch-size must be greater than zero");
        }

        if (maxBatchesPerRun <= 0) {
            throw new IllegalArgumentException(
                    "inventory.sweeper.max-batches-per-run must be greater than zero");
        }

        this.expiryService = expiryService;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @Scheduled(
            fixedDelayString = "${inventory.sweeper.interval-ms:60000}",
            initialDelayString = "${inventory.sweeper.initial-delay-ms:30000}"
    )
    public void sweep() {
        int total = 0;

        try {
            for (int i = 0; i < maxBatchesPerRun; i++) {
                int processed = expiryService.expireBatch(batchSize);
                total += processed;

                if (processed < batchSize) {
                    break;
                }
            }

            if (total > 0) {
                log.info(
                        "Reservation sweeper expired {} reservation(s)",
                        total
                );
            }
        } catch (RuntimeException ex) {
            log.error(
                    "Reservation sweep failed; will retry on next run",
                    ex
            );
        }
    }
}
