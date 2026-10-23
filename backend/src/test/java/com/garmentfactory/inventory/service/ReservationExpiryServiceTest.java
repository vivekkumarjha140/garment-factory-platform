package com.garmentfactory.inventory.service;

import com.garmentfactory.inventory.entity.*;
import com.garmentfactory.inventory.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationExpiryServiceTest {

    @Mock StockReservationRepository reservationRepository;
    @Mock FinishedGoodsStockRepository stockRepository;
    @Mock StockMovementRepository movementRepository;
    @InjectMocks ReservationExpiryService service;

    private static StockReservation expiredReservation() {
        return new StockReservation(1L, 5L, 3, Instant.now().minusSeconds(60));
    }

    @Test
    void expireBatch_releasesStockAndWritesLedger() {
        StockReservation r = expiredReservation();
        FinishedGoodsStock s = new FinishedGoodsStock(5L, 5);
        ReflectionTestUtils.setField(s, "onHand", 10);
        when(reservationRepository.lockExpiredBatch(100)).thenReturn(List.of(r));
        when(stockRepository.tryRelease(5L, 3)).thenReturn(1);
        when(stockRepository.findByVariantId(5L)).thenReturn(Optional.of(s));

        int processed = service.expireBatch(100);

        assertEquals(1, processed);
        assertEquals(ReservationStatus.EXPIRED, r.getStatus());
        ArgumentCaptor<StockMovement> cap = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(cap.capture());
        assertEquals(MovementType.RELEASE, cap.getValue().getMovementType());
        assertEquals(-3, cap.getValue().getReservedDelta().intValue());
        assertNull(cap.getValue().getActorUserId());
    }

    @Test
    void expireBatch_emptyBatch_doesNothing() {
        when(reservationRepository.lockExpiredBatch(100)).thenReturn(List.of());

        assertEquals(0, service.expireBatch(100));
        verifyNoInteractions(stockRepository, movementRepository);
    }

    @Test
    void expireBatch_outOfSyncCounter_throwsAndDoesNotWriteLedger() {
        StockReservation reservation = expiredReservation();

        when(reservationRepository.lockExpiredBatch(100))
                .thenReturn(List.of(reservation));
        when(stockRepository.tryRelease(5L, 3))
                .thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.expireBatch(100));

        // The in-memory entity was changed before the exception.
        // The real transaction must roll back its persisted state.
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());

        verify(movementRepository, never()).save(any());
    }
}
