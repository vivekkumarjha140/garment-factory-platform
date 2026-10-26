package com.garmentfactory.customer.service;

import com.garmentfactory.customer.api.ShippingAddressLookup;
import com.garmentfactory.customer.api.ShippingTarget;
import com.garmentfactory.customer.entity.Address;
import com.garmentfactory.customer.repository.AddressRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShippingAddressLookupService implements ShippingAddressLookup {

    private final AddressRepository addresses;

    public ShippingAddressLookupService(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Override
    public Optional<ShippingTarget> findForUser(long userId, String addressPublicId) {
        if (addressPublicId == null || addressPublicId.isBlank()) {
            return Optional.empty();
        }
        return addresses.findByPublicIdAndUserId(addressPublicId.trim(), userId).map(ShippingAddressLookupService::toTarget);
    }

    @Override
    public Optional<ShippingTarget> findDefaultShipping(long userId) {
        return addresses.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .filter(Address::isDefaultShipping)
                .findFirst()
                .map(ShippingAddressLookupService::toTarget);
    }

    private static ShippingTarget toTarget(Address a) {
        return new ShippingTarget(a.getPublicId(), a.getState(), a.getPincode());
    }
}