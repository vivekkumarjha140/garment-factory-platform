package com.garmentfactory.inventory.service;

import com.garmentfactory.inventory.entity.FinishedGoodsStock;
import com.garmentfactory.inventory.entity.MovementType;
import com.garmentfactory.inventory.entity.StockMovement;
import com.garmentfactory.inventory.entity.StockReservation;
import com.garmentfactory.inventory.repository.FinishedGoodsStockRepository;
import com.garmentfactory.inventory.repository.StockMovementRepository;
import com.garmentfactory.inventory.repository.StockReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationExpiryService {

    private static final Logger log =
            LoggerFactory.getLogger(ReservationExpiryService.class);

    private final StockReservationRepository reservationRepository;
    private final FinishedGoodsStockRepository stockRepository;
    private final StockMovementRepository movementRepository;

    public ReservationExpiryService(
            StockReservationRepository reservationRepository,
            FinishedGoodsStockRepository stockRepository,
            StockMovementRepository movementRepository) {
        this.reservationRepository = reservationRepository;
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
    }

    /**
     * One batch = one transaction.
     * Any stock-counter inconsistency fails the whole batch so that
     * reservation status, stock counters and ledger remain consistent.
     */
    @Transactional
    public int expireBatch(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be greater than zero");
        }

        List<StockReservation> batch =
                reservationRepository.lockExpiredBatch(batchSize);

        for (StockReservation reservation : batch) {
            reservation.markExpired();
            reservationRepository.saveAndFlush(reservation);

            if (stockRepository.tryRelease(
                    reservation.getVariantId(),
                    reservation.getQuantity()) == 0) {

                log.error(
                        "Reserved counter out of sync while expiring reservation {} "
                                + "(variant {}, qty {}). Rolling back batch.",
                        reservation.getPublicId(),
                        reservation.getVariantId(),
                        reservation.getQuantity());

                throw new IllegalStateException(
                        "Stock inconsistency while expiring reservation "
                                + reservation.getPublicId());
            }

            FinishedGoodsStock stock = stockRepository
                    .findByVariantId(reservation.getVariantId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Stock record missing for variant "
                                    + reservation.getVariantId()
                                    + " while expiring reservation "
                                    + reservation.getPublicId()));

            movementRepository.save(
                    StockMovement.forFinishedGood(
                            reservation.getVariantId(),
                            MovementType.RELEASE,
                            0,
                            -reservation.getQuantity(),
                            0,
                            stock.getOnHand(),
                            "ORDER",
                            String.valueOf(reservation.getOrderId()),
                            "Reservation expired",
                            null));
        }

        return batch.size();
    }
}
