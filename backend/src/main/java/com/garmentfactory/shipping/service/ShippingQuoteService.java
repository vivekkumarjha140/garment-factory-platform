package com.garmentfactory.shipping.service;

import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shipping.api.ShippingQuote;
import com.garmentfactory.shipping.api.ShippingQuoteApi;
import com.garmentfactory.shipping.entity.DeliveryZone;
import com.garmentfactory.shipping.entity.ShippingRate;
import com.garmentfactory.shipping.entity.ZoneRule;
import com.garmentfactory.shipping.entity.ZoneRuleType;
import com.garmentfactory.shipping.repository.DeliveryZoneRepository;
import com.garmentfactory.shipping.repository.ShippingRateRepository;
import com.garmentfactory.shipping.repository.ZoneRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ShippingQuoteService implements ShippingQuoteApi {

    private static final Pattern PIN = Pattern.compile("^[1-9][0-9]{5}$");

    private final DeliveryZoneRepository zoneRepository;
    private final ZoneRuleRepository ruleRepository;
    private final ShippingRateRepository rateRepository;

    public ShippingQuoteService(DeliveryZoneRepository zoneRepository,
                                ZoneRuleRepository ruleRepository,
                                ShippingRateRepository rateRepository) {
        this.zoneRepository = zoneRepository;
        this.ruleRepository = ruleRepository;
        this.rateRepository = rateRepository;
    }

    @Override
    public ShippingQuote quote(String state, String pincode, int totalWeightGrams, BigDecimal orderValue) {
        if (pincode == null || !PIN.matcher(pincode).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PINCODE",
                    "Enter a valid 6-digit PIN code.") {
            };
        }
        if (totalWeightGrams < 0) {
            throw new IllegalArgumentException("totalWeightGrams must be >= 0");
        }

        DeliveryZone zone = resolveZone(state, pincode)
                .orElseThrow(() -> deliveryNotAvailable());

        ShippingRate rate = rateRepository.findMatchingSlabs(zone.getId(), totalWeightGrams).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SHIPPING_RATE_NOT_FOUND",
                        "No shipping rate is configured for this parcel weight. Please contact us.") {
                });

        boolean free = zone.getFreeShippingAbove() != null
                && orderValue.compareTo(zone.getFreeShippingAbove()) >= 0;
        BigDecimal charge = free ? BigDecimal.ZERO.setScale(2) : rate.getCharge();

        return new ShippingQuote(zone.getCode(), zone.getName(), charge, free,
                rate.getEstimatedDaysMin(), rate.getEstimatedDaysMax());
    }

    /** Longest matching PIN prefix wins; otherwise STATE rule; otherwise empty. Inactive zone = not deliverable. */
    private Optional<DeliveryZone> resolveZone(String state, String pincode) {
        List<String> prefixes = new ArrayList<>();
        for (int len = 6; len >= 1; len--) {
            prefixes.add(pincode.substring(0, len));
        }
        Optional<ZoneRule> pinRule = ruleRepository
                .findByRuleTypeAndMatchValueIn(ZoneRuleType.PIN_PREFIX, prefixes).stream()
                .max(Comparator.comparingInt(r -> r.getMatchValue().length()));

        Optional<ZoneRule> chosen = pinRule;
        if (chosen.isEmpty() && state != null && !state.isBlank()) {
            chosen = ruleRepository.findByRuleTypeAndMatchValue(ZoneRuleType.STATE, state.trim());
        }
        return chosen
                .flatMap(r -> zoneRepository.findById(r.getZoneId()))
                .filter(DeliveryZone::isActive);
    }

    private static ApiException deliveryNotAvailable() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DELIVERY_NOT_AVAILABLE",
                "We do not deliver to this location yet.") {
        };
    }
}