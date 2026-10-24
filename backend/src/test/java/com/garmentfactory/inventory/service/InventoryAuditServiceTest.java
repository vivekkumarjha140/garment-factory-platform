
package com.garmentfactory.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.inventory.entity.AuditStatus;
import com.garmentfactory.inventory.entity.FinishedGoodsStock;
import com.garmentfactory.inventory.entity.InventoryAudit;
import com.garmentfactory.inventory.entity.InventoryAuditLine;
import com.garmentfactory.inventory.entity.MovementType;
import com.garmentfactory.inventory.entity.StockItemType;
import com.garmentfactory.inventory.entity.StockMovement;
import com.garmentfactory.inventory.exception.InsufficientStockException;
import com.garmentfactory.inventory.exception.InvalidStockOperationException;
import com.garmentfactory.inventory.exception.StockConflictException;
import com.garmentfactory.inventory.repository.FinishedGoodsStockRepository;
import com.garmentfactory.inventory.repository.InventoryAuditLineRepository;
import com.garmentfactory.inventory.repository.InventoryAuditRepository;
import com.garmentfactory.inventory.repository.RawMaterialRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryAuditServiceTest {

    private static final String PUBLIC_ID = "pub-1";
    private static final String REASON = "Inventory audit AUD-1 variance";

    @Mock
    InventoryAuditRepository auditRepository;

    @Mock
    InventoryAuditLineRepository lineRepository;

    @Mock
    FinishedGoodsStockRepository stockRepository;

    @Mock
    RawMaterialRepository rawMaterialRepository;

    @Mock
    FinishedGoodsStockService stockService;

    @Mock
    RawMaterialStockService rawMaterialService;

    @Mock
    AuditRecorder auditRecorder;

    @InjectMocks
    InventoryAuditService service;

    private static InventoryAudit audit() {
        InventoryAudit a = new InventoryAudit("AUD-1", 9L, null);
        ReflectionTestUtils.setField(a, "id", 1L);
        ReflectionTestUtils.setField(a, "publicId", PUBLIC_ID);
        return a;
    }

    private static StockMovement movement(long id) {
        StockMovement m = StockMovement.forFinishedGood(
                7L,
                MovementType.ADJUSTMENT,
                0,
                0,
                0,
                0,
                null,
                null,
                "x",
                9L
        );

        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void start_createsInProgressAuditWithTrimmedNotes() {
        when(auditRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InventoryAudit a = service.start("  Q4 count ", 9L);

        assertEquals(AuditStatus.IN_PROGRESS, a.getStatus());
        assertEquals("Q4 count", a.getNotes());
        assertTrue(a.getAuditNumber().startsWith("AUD-"));
    }

    @Test
    void recordFinishedGoodCount_snapshotsOnHandAndComputesVariance() {
        FinishedGoodsStock stock = new FinishedGoodsStock(7L, 5);
        ReflectionTestUtils.setField(stock, "onHand", 10);

        when(auditRepository.findByPublicId(PUBLIC_ID))
                .thenReturn(Optional.of(audit()));
        when(stockRepository.findByVariantId(7L))
                .thenReturn(Optional.of(stock));
        when(lineRepository.findByAuditIdAndVariantId(1L, 7L))
                .thenReturn(Optional.empty());
        when(lineRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        InventoryAuditLine line =
                service.recordFinishedGoodCount(PUBLIC_ID, 7L, 8);

        assertEquals(0, bd("10").compareTo(line.getSystemQuantity()));
        assertEquals(0, bd("-2").compareTo(line.getVariance()));
    }

    @Test
    void recordFinishedGoodCount_replacesExistingLineForSameItem() {
        FinishedGoodsStock stock = new FinishedGoodsStock(7L, 5);

        InventoryAuditLine previous = new InventoryAuditLine(
                1L,
                StockItemType.FINISHED_GOOD,
                7L,
                null,
                bd("0"),
                bd("3")
        );

        when(auditRepository.findByPublicId(PUBLIC_ID))
                .thenReturn(Optional.of(audit()));
        when(stockRepository.findByVariantId(7L))
                .thenReturn(Optional.of(stock));
        when(lineRepository.findByAuditIdAndVariantId(1L, 7L))
                .thenReturn(Optional.of(previous));
        when(lineRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        service.recordFinishedGoodCount(PUBLIC_ID, 7L, 4);

        verify(lineRepository).delete(previous);
    }

    @Test
    void recordCount_onCompletedAudit_isConflict() {
        InventoryAudit done = audit();
        done.complete();

        when(auditRepository.findByPublicId(PUBLIC_ID))
                .thenReturn(Optional.of(done));

        assertThrows(
                StockConflictException.class,
                () -> service.recordFinishedGoodCount(PUBLIC_ID, 7L, 1)
        );

        verifyNoInteractions(stockRepository, lineRepository);
    }

    @Test
    void complete_withApply_adjustsOnlyVarianceLinesAndLinksMovements() {
        InventoryAudit audit = audit();

        InventoryAuditLine fg = new InventoryAuditLine(
                1L,
                StockItemType.FINISHED_GOOD,
                7L,
                null,
                bd("10"),
                bd("8")
        );

        InventoryAuditLine unchanged = new InventoryAuditLine(
                1L,
                StockItemType.FINISHED_GOOD,
                8L,
                null,
                bd("5"),
                bd("5")
        );

        InventoryAuditLine raw = new InventoryAuditLine(
                1L,
                StockItemType.RAW_MATERIAL,
                null,
                3L,
                bd("12.500"),
                bd("14.000")
        );

        when(auditRepository.findByPublicId(PUBLIC_ID))
                .thenReturn(Optional.of(audit));
        when(lineRepository.findByAuditIdOrderByIdAsc(1L))
                .thenReturn(List.of(fg, unchanged, raw));
        when(stockService.adjustForAudit(7L, -2, REASON, PUBLIC_ID, 9L))
                .thenReturn(movement(55L));
        when(rawMaterialService.adjustForAudit(
                eq(3L),
                eq(bd("1.500")),
                eq(REASON),
                eq(PUBLIC_ID),
                eq(9L)
        )).thenReturn(movement(56L));
        when(lineRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));
        when(auditRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        InventoryAuditService.AuditCompletion result =
                service.complete(PUBLIC_ID, true, 9L);

        assertEquals(2, result.adjustmentsApplied());
        assertEquals(AuditStatus.COMPLETED, result.audit().getStatus());
        assertEquals(55L, fg.getAdjustmentMovementId());
        assertEquals(56L, raw.getAdjustmentMovementId());
        assertNull(unchanged.getAdjustmentMovementId());

        verify(
                stockService,
                never()
        ).adjustForAudit(
                eq(8L),
                any(Integer.class),
                any(),
                any(),
                any()
        );

        verify(auditRecorder).record(any(AuditEvent.class));
    }

    @Test
    void complete_withoutApply_onlyClosesAudit() {
        InventoryAudit audit = audit();

        InventoryAuditLine fg = new InventoryAuditLine(
                1L,
                StockItemType.FINISHED_GOOD,
                7L,
                null,
                bd("10"),
                bd("8")
        );

        when(auditRepository.findByPublicId(PUBLIC_ID))
                .thenReturn(Optional.of(audit));
        when(lineRepository.findByAuditIdOrderByIdAsc(1L))
                .thenReturn(List.of(fg));
        when(auditRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        InventoryAuditService.AuditCompletion result =
                service.complete(PUBLIC_ID, false, 9L);

        assertEquals(0, result.adjustmentsApplied());
        assertEquals(AuditStatus.COMPLETED, result.audit().getStatus());

        verifyNoInteractions(stockService, rawMaterialService);
    }

    @Test
    void complete_withNoLines_isRejected() {
        InventoryAudit audit = audit();

        when(auditRepository.findByPublicId(PUBLIC_ID))
                .thenReturn(Optional.of(audit));
        when(lineRepository.findByAuditIdOrderByIdAsc(1L))
                .thenReturn(List.of());

        assertThrows(
                InvalidStockOperationException.class,
                () -> service.complete(PUBLIC_ID, true, 9L)
        );

        assertEquals(AuditStatus.IN_PROGRESS, audit.getStatus());
    }

    @Test
    void complete_withDuplicateLines_isConflictAndAdjustsNothing() {
        InventoryAuditLine a = new InventoryAuditLine(
                1L,
                StockItemType.FINISHED_GOOD,
                7L,
                null,
                bd("10"),
                bd("8")
        );

        InventoryAuditLine b = new InventoryAuditLine(
                1L,
                StockItemType.FINISHED_GOOD,
                7L,
                null,
                bd("10"),
                bd("9")
        );

        when(auditRepository.findByPublicId(PUBLIC_ID))
                .thenReturn(Optional.of(audit()));
        when(lineRepository.findByAuditIdOrderByIdAsc(1L))
                .thenReturn(List.of(a, b));

        assertThrows(
                StockConflictException.class,
                () -> service.complete(PUBLIC_ID, true, 9L)
        );

        verifyNoInteractions(stockService, rawMaterialService);
    }

    @Test
    void complete_whenAnAdjustmentIsRejected_auditStaysInProgress() {
        InventoryAudit audit = audit();

        InventoryAuditLine fg = new InventoryAuditLine(
                1L,
                StockItemType.FINISHED_GOOD,
                7L,
                null,
                bd("10"),
                bd("2")
        );

        when(auditRepository.findByPublicId(PUBLIC_ID))
                .thenReturn(Optional.of(audit));
        when(lineRepository.findByAuditIdOrderByIdAsc(1L))
                .thenReturn(List.of(fg));
        when(stockService.adjustForAudit(7L, -8, REASON, PUBLIC_ID, 9L))
                .thenThrow(
                        new InsufficientStockException(
                                "would drop below reserved"
                        )
                );

        assertThrows(
                InsufficientStockException.class,
                () -> service.complete(PUBLIC_ID, true, 9L)
        );

        assertEquals(AuditStatus.IN_PROGRESS, audit.getStatus());
        verify(auditRepository, never()).save(any());
        verify(auditRecorder, never()).record(any(AuditEvent.class));
    }
}
