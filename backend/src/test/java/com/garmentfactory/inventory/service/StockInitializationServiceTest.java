package com.garmentfactory.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.garmentfactory.catalog.api.VariantLookup;
import com.garmentfactory.catalog.api.VariantSummary;
import com.garmentfactory.inventory.entity.FinishedGoodsStock;
import com.garmentfactory.inventory.exception.InvalidStockOperationException;
import com.garmentfactory.inventory.repository.FinishedGoodsStockRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class StockInitializationServiceTest {

    @Mock VariantLookup variantLookup;
    @Mock FinishedGoodsStockRepository stockRepository;
    @Mock FinishedGoodsStockService stockService;
    @InjectMocks StockInitializationService service;

    private static VariantSummary variant(long id) {
        return new VariantSummary(id, "pub-" + id, "SKU-" + id, "M", "Red", "ACTIVE", "prod-1", "Tee");
    }

    @Test
    void initialize_unknownVariant_is404AndCreatesNothing() {
        when(variantLookup.findById(7L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.initialize(7L, 5));

        assertEquals("VARIANT_NOT_FOUND", ex.getCode());
        verifyNoInteractions(stockService);
    }

    @Test
    void initialize_knownVariant_delegatesToTheStockService() {
        FinishedGoodsStock stock = new FinishedGoodsStock(7L, 5);
        when(variantLookup.findById(7L)).thenReturn(Optional.of(variant(7)));
        when(stockService.initializeStock(7L, 5)).thenReturn(stock);

        assertEquals(stock, service.initialize(7L, 5));
    }

    @Test
    void findUninitialized_dropsVariantsThatAlreadyHaveStock() {
        when(variantLookup.search("tee", 100)).thenReturn(List.of(variant(1), variant(2), variant(3)));
        when(stockRepository.findByVariantIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(new FinishedGoodsStock(2L, 5)));

        List<VariantSummary> result = service.findUninitialized("tee", 10);

        assertEquals(List.of(1L, 3L), result.stream().map(VariantSummary::id).toList());
    }

    @Test
    void initializeMissing_createsOnlyMissingRowsAndPagesThroughTheCatalog() {
        when(variantLookup.variantIdsAfter(0L, 500)).thenReturn(List.of(1L, 2L, 3L));
        when(variantLookup.variantIdsAfter(3L, 500)).thenReturn(List.of());
        when(stockRepository.findByVariantIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(new FinishedGoodsStock(2L, 5)));

        int created = service.initializeMissing(10);

        assertEquals(2, created);
        verify(stockService).initializeStock(1L, 10);
        verify(stockService).initializeStock(3L, 10);
        verify(stockService, never()).initializeStock(2L, 10);
    }

    @Test
    void initializeMissing_ignoresRowsCreatedConcurrentlyByAnotherRequest() {
        when(variantLookup.variantIdsAfter(0L, 500)).thenReturn(List.of(1L));
        when(variantLookup.variantIdsAfter(1L, 500)).thenReturn(List.of());
        when(stockRepository.findByVariantIdIn(List.of(1L))).thenReturn(List.of());
        when(stockService.initializeStock(1L, 10)).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertEquals(0, service.initializeMissing(10));
    }

    @Test
    void initializeMissing_negativeThreshold_isRejectedBeforeAnyWork() {
        assertThrows(InvalidStockOperationException.class, () -> service.initializeMissing(-1));
        verifyNoInteractions(variantLookup, stockService);
    }
}