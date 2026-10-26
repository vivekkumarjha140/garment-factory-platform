package com.garmentfactory.customer.service;

import com.garmentfactory.customer.api.CheckoutAddress;
import com.garmentfactory.customer.api.CheckoutAddressLookup;
import com.garmentfactory.customer.repository.AddressRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CheckoutAddressLookupService implements CheckoutAddressLookup {

    private final AddressRepository addresses;

    public CheckoutAddressLookupService(AddressRepository addresses) {
        this.addresses = addresses;
    }

    @Override
    public Optional<CheckoutAddress> findForUser(long userId, String addressPublicId) {
        if (addressPublicId == null || addressPublicId.isBlank()) {
            return Optional.empty();
        }
        return addresses.findByPublicIdAndUserId(addressPublicId.trim(), userId)
                .map(a -> new CheckoutAddress(a.getPublicId(), a.getRecipientName(), a.getPhone(), a.getLine1(),
                        a.getLine2(), a.getCity(), a.getState(), a.getPincode()));
    }
}