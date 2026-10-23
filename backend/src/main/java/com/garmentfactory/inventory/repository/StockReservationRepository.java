package com.garmentfactory.inventory.repository;

import com.garmentfactory.inventory.entity.ReservationStatus;
import com.garmentfactory.inventory.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    List<StockReservation> findByOrderId(Long orderId);

    /**
     * Sweeper query. Locks a batch of expired ACTIVE reservations; rows already locked by
     * another sweeper instance are skipped, so multiple nodes never double-release.
     * Must be called inside an open transaction (MANDATORY), otherwise the lock is meaningless.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            SELECT * FROM stock_reservations
             WHERE status = 'ACTIVE' AND expires_at < UTC_TIMESTAMP(6)
             ORDER BY expires_at, id
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<StockReservation> lockExpiredBatch(@Param("batchSize") int batchSize);
}
