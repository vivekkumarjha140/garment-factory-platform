package com.garmentfactory.shipping.service;

import com.garmentfactory.shipping.api.ShippingQuote;
import com.garmentfactory.shipping.entity.DeliveryZone;
import com.garmentfactory.shipping.entity.ShippingRate;
import com.garmentfactory.shipping.entity.ZoneRule;
import com.garmentfactory.shipping.entity.ZoneRuleType;
import com.garmentfactory.shipping.repository.DeliveryZoneRepository;
import com.garmentfactory.shipping.repository.ShippingRateRepository;
import com.garmentfactory.shipping.repository.ZoneRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ShippingQuoteServiceTest {

    private DeliveryZoneRepository zones;
    private ZoneRuleRepository rules;
    private ShippingRateRepository rates;
    private ShippingQuoteService service;

    private DeliveryZone ncr;
    private DeliveryZone north;

    @BeforeEach
    void setUp() {
        zones = Mockito.mock(DeliveryZoneRepository.class);
        rules = Mockito.mock(ZoneRuleRepository.class);
        rates = Mockito.mock(ShippingRateRepository.class);
        service = new ShippingQuoteService(zones, rules, rates);

        ncr = new DeliveryZone("NCR", "Delhi NCR");
        ReflectionTestUtils.setField(ncr, "id", 1L);
        north = new DeliveryZone("NORTH", "North India");
        ReflectionTestUtils.setField(north, "id", 2L);
        Mockito.when(zones.findById(1L)).thenReturn(Optional.of(ncr));
        Mockito.when(zones.findById(2L)).thenReturn(Optional.of(north));
    }

    private void slab(long zoneId, int min, int max, String charge) {
        Mockito.when(rates.findMatchingSlabs(eq(zoneId), any(Integer.class)))
                .thenReturn(List.of(new ShippingRate(zoneId, min, max, new BigDecimal(charge))));
    }

    @Test
    void longestPinPrefixBeatsShorterOne() {
        Mockito.when(rules.findByRuleTypeAndMatchValueIn(eq(ZoneRuleType.PIN_PREFIX), any()))
                .thenReturn(List.of(new ZoneRule(2L, ZoneRuleType.PIN_PREFIX, "12"),
                        new ZoneRule(1L, ZoneRuleType.PIN_PREFIX, "1220")));
        slab(1L, 0, 1000, "60.00");

        ShippingQuote q = service.quote(null, "122001", 500, new BigDecimal("800"));
        assertThat(q.zoneCode()).isEqualTo("NCR");
        assertThat(q.charge()).isEqualByComparingTo("60.00");
        assertThat(q.freeShippingApplied()).isFalse();
    }

    @Test
    void fallsBackToStateRuleWhenNoPinRule() {
        Mockito.when(rules.findByRuleTypeAndMatchValueIn(eq(ZoneRuleType.PIN_PREFIX), any()))
                .thenReturn(List.of());
        Mockito.when(rules.findByRuleTypeAndMatchValue(ZoneRuleType.STATE, "Punjab"))
                .thenReturn(Optional.of(new ZoneRule(2L, ZoneRuleType.STATE, "Punjab")));
        slab(2L, 0, 1000, "90.00");

        assertThat(service.quote("Punjab", "140001", 300, new BigDecimal("500")).zoneCode()).isEqualTo("NORTH");
    }

    @Test
    void freeShippingAboveThreshold() {
        ncr.setFreeShippingAbove(new BigDecimal("999"));
        Mockito.when(rules.findByRuleTypeAndMatchValueIn(eq(ZoneRuleType.PIN_PREFIX), any()))
                .thenReturn(List.of(new ZoneRule(1L, ZoneRuleType.PIN_PREFIX, "1220")));
        slab(1L, 0, 1000, "60.00");

        ShippingQuote q = service.quote(null, "122001", 400, new BigDecimal("999"));
        assertThat(q.freeShippingApplied()).isTrue();
        assertThat(q.charge()).isEqualByComparingTo("0.00");
    }

    @Test
    void unknownLocationIsNotDeliverable() {
        Mockito.when(rules.findByRuleTypeAndMatchValueIn(eq(ZoneRuleType.PIN_PREFIX), any()))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.quote("Nowhere", "799999", 100, BigDecimal.TEN))
                .hasFieldOrPropertyWithValue("code", "DELIVERY_NOT_AVAILABLE");
    }

    @Test
    void inactiveZoneIsNotDeliverable() {
        ncr.setActive(false);
        Mockito.when(rules.findByRuleTypeAndMatchValueIn(eq(ZoneRuleType.PIN_PREFIX), any()))
                .thenReturn(List.of(new ZoneRule(1L, ZoneRuleType.PIN_PREFIX, "1220")));
        assertThatThrownBy(() -> service.quote(null, "122001", 100, BigDecimal.TEN))
                .hasFieldOrPropertyWithValue("code", "DELIVERY_NOT_AVAILABLE");
    }

    @Test
    void parcelHeavierThanAnySlabFails() {
        Mockito.when(rules.findByRuleTypeAndMatchValueIn(eq(ZoneRuleType.PIN_PREFIX), any()))
                .thenReturn(List.of(new ZoneRule(1L, ZoneRuleType.PIN_PREFIX, "1220")));
        Mockito.when(rates.findMatchingSlabs(eq(1L), any(Integer.class))).thenReturn(List.of());
        assertThatThrownBy(() -> service.quote(null, "122001", 99_000, BigDecimal.TEN))
                .hasFieldOrPropertyWithValue("code", "SHIPPING_RATE_NOT_FOUND");
    }

    @Test
    void invalidPinIsRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> service.quote(null, "012345", 100, BigDecimal.TEN))
                .hasFieldOrPropertyWithValue("code", "INVALID_PINCODE");
        Mockito.verifyNoInteractions(rules, rates);
    }
}