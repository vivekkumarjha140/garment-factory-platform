package com.garmentfactory.inventory.service;

import com.garmentfactory.inventory.entity.*;
import com.garmentfactory.inventory.exception.*;
import com.garmentfactory.inventory.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinishedGoodsStockServiceTest {

    @Mock FinishedGoodsStockRepository stockRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock StockReservationRepository reservationRepository;
    @Mock StockReceiptRepository receiptRepository;

    FinishedGoodsStockService service;

    @BeforeEach
    void setUp() {
        service = new FinishedGoodsStockService(stockRepository, movementRepository,
                reservationRepository, receiptRepository, 30L);
    }

    private static FinishedGoodsStock stock(long variantId, int onHand, int reserved) {
        FinishedGoodsStock s = new FinishedGoodsStock(variantId, 5);
        ReflectionTestUtils.setField(s, "onHand", onHand);
        ReflectionTestUtils.setField(s, "reserved", reserved);
        return s;
    }

    @Test
    void reserve_success_createsReservationAndLedgerEntry() {
        when(reservationRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(stockRepository.tryReserve(7L, 2)).thenReturn(1);
        when(stockRepository.findByVariantId(7L)).thenReturn(Optional.of(stock(7, 10, 2)));

        StockReservation r = service.reserve(100L, 7L, 2, 9L);

        assertEquals(ReservationStatus.ACTIVE, r.getStatus());
        assertEquals(2, r.getQuantity());
        ArgumentCaptor<StockMovement> cap = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(cap.capture());
        StockMovement m = cap.getValue();
        assertEquals(MovementType.RESERVATION, m.getMovementType());
        assertEquals(0, m.getOnHandDelta().intValue());
        assertEquals(2, m.getReservedDelta().intValue());
        assertEquals(10, m.getBalanceOnHandAfter().intValue());
        assertEquals("ORDER", m.getReferenceType());
        assertEquals("100", m.getReferenceId());
        assertEquals(9L, m.getActorUserId());
    }

    @Test
    void reserve_insufficientStock_throwsConflictAndWritesNoLedger() {
        when(stockRepository.tryReserve(7L, 2)).thenReturn(0);
        when(stockRepository.findByVariantId(7L)).thenReturn(Optional.of(stock(7, 1, 0)));

        assertThrows(InsufficientStockException.class, () -> service.reserve(100L, 7L, 2, 9L));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void reserve_unknownVariant_throwsNotFound() {
        when(stockRepository.tryReserve(7L, 1)).thenReturn(0);
        when(stockRepository.findByVariantId(7L)).thenReturn(Optional.empty());

        assertThrows(StockNotFoundException.class, () -> service.reserve(100L, 7L, 1, 9L));
    }

    @Test
    void reserve_nonPositiveQuantity_isRejectedBeforeAnyDbCall() {
        assertThrows(InvalidStockOperationException.class, () -> service.reserve(100L, 7L, 0, 9L));
        verifyNoInteractions(stockRepository, movementRepository, reservationRepository);
    }

    @Test
    void reserveAll_locksVariantsInAscendingOrder() {
        when(reservationRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(stockRepository.tryReserve(anyLong(), anyInt())).thenReturn(1);
        when(stockRepository.findByVariantId(anyLong())).thenAnswer(i -> {
            Long v = i.getArgument(0);
            return Optional.of(stock(v, 100, 0));
        });

        List<StockReservation> result = service.reserveAll(1L,
                List.of(new ReservationLine(30L, 1), new ReservationLine(10L, 1), new ReservationLine(20L, 1)), 9L);

        assertEquals(3, result.size());
        InOrder order = inOrder(stockRepository);
        order.verify(stockRepository).tryReserve(10L, 1);
        order.verify(stockRepository).tryReserve(20L, 1);
        order.verify(stockRepository).tryReserve(30L, 1);
    }

    @Test
    void reserveAll_mergesDuplicateVariantLines() {
        when(reservationRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(stockRepository.tryReserve(5L, 3)).thenReturn(1);
        when(stockRepository.findByVariantId(5L)).thenReturn(Optional.of(stock(5, 50, 3)));

        List<StockReservation> result = service.reserveAll(1L,
                List.of(new ReservationLine(5L, 1), new ReservationLine(5L, 2)), 9L);

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getQuantity());
    }

    @Test
    void adjust_blankReason_isRejected() {
        assertThrows(InvalidStockOperationException.class, () -> service.adjust(7L, 5, "  ", 9L));
        verifyNoInteractions(stockRepository, movementRepository);
    }

    @Test
    void adjust_zeroDelta_isRejected() {
        assertThrows(InvalidStockOperationException.class, () -> service.adjust(7L, 0, "recount", 9L));
    }

    @Test
    void adjust_belowReserved_throwsConflict() {
        when(stockRepository.tryAdjust(7L, -8)).thenReturn(0);
        when(stockRepository.findByVariantId(7L)).thenReturn(Optional.of(stock(7, 10, 5)));

        assertThrows(InsufficientStockException.class, () -> service.adjust(7L, -8, "shrinkage", 9L));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void adjust_success_writesAdjustmentLedger() {
        when(stockRepository.tryAdjust(7L, -3)).thenReturn(1);
        when(stockRepository.findByVariantId(7L)).thenReturn(Optional.of(stock(7, 7, 0)));

        service.adjust(7L, -3, " cycle count ", 9L);

        ArgumentCaptor<StockMovement> cap = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(cap.capture());
        StockMovement m = cap.getValue();
        assertEquals(MovementType.ADJUSTMENT, m.getMovementType());
        assertEquals(-3, m.getOnHandDelta().intValue());
        assertEquals(7, m.getBalanceOnHandAfter().intValue());
        assertEquals("cycle count", m.getReason());
    }

    @Test
    void markDamaged_success_movesStockToDamagedBucket() {
        when(stockRepository.tryMarkDamaged(7L, 2)).thenReturn(1);
        when(stockRepository.findByVariantId(7L)).thenReturn(Optional.of(stock(7, 8, 0)));

        service.markDamaged(7L, 2, "water damage", 9L);

        ArgumentCaptor<StockMovement> cap = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(cap.capture());
        StockMovement m = cap.getValue();
        assertEquals(MovementType.DAMAGE, m.getMovementType());
        assertEquals(-2, m.getOnHandDelta().intValue());
        assertEquals(2, m.getDamagedDelta().intValue());
    }
}
