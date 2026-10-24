package com.garmentfactory.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.garmentfactory.catalog.api.VariantLookup;
import com.garmentfactory.catalog.api.VariantSummary;
import com.garmentfactory.inventory.entity.InventoryAuditLine;
import com.garmentfactory.inventory.entity.RawMaterial;
import com.garmentfactory.inventory.entity.RawMaterialCategory;
import com.garmentfactory.inventory.entity.StockItemType;
import com.garmentfactory.inventory.repository.RawMaterialRepository;
import com.garmentfactory.inventory.service.AuditLineLabelService.ItemLabel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuditLineLabelServiceTest {

    @Mock VariantLookup variantLookup;
    @Mock RawMaterialRepository rawMaterialRepository;
    @InjectMocks AuditLineLabelService service;

    private static InventoryAuditLine finishedGoodLine(long id, long variantId) {
        InventoryAuditLine line = new InventoryAuditLine(1L, StockItemType.FINISHED_GOOD, variantId, null,
                BigDecimal.TEN, BigDecimal.ONE);
        ReflectionTestUtils.setField(line, "id", id);
        return line;
    }

    private static InventoryAuditLine rawLine(long id, long rawId) {
        InventoryAuditLine line = new InventoryAuditLine(1L, StockItemType.RAW_MATERIAL, null, rawId,
                BigDecimal.TEN, BigDecimal.ONE);
        ReflectionTestUtils.setField(line, "id", id);
        return line;
    }

    @Test
    void labelsVariantsFromTheCatalogAndRawMaterialsFromTheMasterList() {
        RawMaterial jersey = new RawMaterial("FAB-JERSEY", "Cotton Jersey", RawMaterialCategory.FABRIC, "METER",
                BigDecimal.ZERO, BigDecimal.ZERO);
        ReflectionTestUtils.setField(jersey, "id", 3L);
        when(variantLookup.findByIds(any())).thenReturn(Map.of(7L,
                new VariantSummary(7L, "pub-7", "TEE-M-RED", "M", "Red", "ACTIVE", "prod-1", "Round Neck Tee")));
        when(rawMaterialRepository.findAllById(any())).thenReturn(List.of(jersey));

        Map<Long, ItemLabel> labels = service.labelsByLineId(List.of(finishedGoodLine(10, 7), rawLine(11, 3)));

        assertEquals(new ItemLabel("Round Neck Tee (M / Red)", "TEE-M-RED"), labels.get(10L));
        assertEquals(new ItemLabel("Cotton Jersey", "FAB-JERSEY"), labels.get(11L));
    }

    @Test
    void unknownItemsGetAnIdFallbackInsteadOfFailing() {
        when(variantLookup.findByIds(any())).thenReturn(Map.of());

        ItemLabel label = service.labelsByLineId(List.of(finishedGoodLine(10, 99))).get(10L);

        assertEquals("Variant #99", label.name());
        assertNull(label.sku());
    }

    @Test
    void doesNotTouchTheCatalogForRawMaterialOnlyAudits() {
        when(rawMaterialRepository.findAllById(any())).thenReturn(List.of());

        service.labelsByLineId(List.of(rawLine(11, 3)));

        verifyNoInteractions(variantLookup);
    }
}