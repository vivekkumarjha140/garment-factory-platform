package com.garmentfactory.customer.service;

import com.garmentfactory.customer.dto.CustomerDtos.AddressRequest;
import com.garmentfactory.customer.dto.CustomerDtos.AddressResponse;
import com.garmentfactory.customer.entity.Address;
import com.garmentfactory.customer.repository.AddressRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer's address book. Every change first locks the customer's profile row, so the 10-address
 * limit and the "exactly one default" rules hold even when requests arrive at the same moment.
 * Every lookup is scoped to the owner: someone else's address is simply "not found".
 */
@Service
public class AddressService {

    public static final int MAX_ADDRESSES = 10;

    public enum DefaultKind {
        SHIPPING,
        BILLING
    }

    private final AddressRepository addresses;
    private final CustomerProfileService profiles;

    public AddressService(AddressRepository addresses, CustomerProfileService profiles) {
        this.addresses = addresses;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(long userId) {
        return addresses.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                // Defaults first; the sort is stable, so the rest stay newest first.
                .sorted(Comparator.comparing((Address a) -> !(a.isDefaultShipping() || a.isDefaultBilling())))
                .map(AddressService::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponse create(long userId, AddressRequest request) {
        profiles.lockForUser(userId);
        long existing = addresses.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw ApiException.conflict("ADDRESS_LIMIT_REACHED", "You can save up to " + MAX_ADDRESSES + " addresses.");
        }

        Address address = new Address();
        address.setUserId(userId);
        apply(address, request);

        boolean first = existing == 0;
        if (first || Boolean.TRUE.equals(request.defaultShipping())) {
            addresses.clearDefaultShipping(userId);
            address.setDefaultShipping(true);
        }
        if (first || Boolean.TRUE.equals(request.defaultBilling())) {
            addresses.clearDefaultBilling(userId);
            address.setDefaultBilling(true);
        }
        return toResponse(addresses.save(address));
    }

    @Transactional
    public AddressResponse update(long userId, String publicId, AddressRequest request) {
        profiles.lockForUser(userId);
        Address address = find(userId, publicId);
        apply(address, request);

        if (Boolean.TRUE.equals(request.defaultShipping()) && !address.isDefaultShipping()) {
            addresses.clearDefaultShipping(userId);
            address.setDefaultShipping(true);
        }
        if (Boolean.TRUE.equals(request.defaultBilling()) && !address.isDefaultBilling()) {
            addresses.clearDefaultBilling(userId);
            address.setDefaultBilling(true);
        }
        return toResponse(address);
    }

    @Transactional
    public AddressResponse makeDefault(long userId, String publicId, DefaultKind kind) {
        profiles.lockForUser(userId);
        Address address = find(userId, publicId);
        if (kind == DefaultKind.SHIPPING && !address.isDefaultShipping()) {
            addresses.clearDefaultShipping(userId);
            address.setDefaultShipping(true);
        } else if (kind == DefaultKind.BILLING && !address.isDefaultBilling()) {
            addresses.clearDefaultBilling(userId);
            address.setDefaultBilling(true);
        }
        return toResponse(address);
    }

    /** Deleting a default promotes the newest remaining address, so a customer with addresses always has defaults. */
    @Transactional
    public void delete(long userId, String publicId) {
        profiles.lockForUser(userId);
        Address address = find(userId, publicId);
        boolean wasShipping = address.isDefaultShipping();
        boolean wasBilling = address.isDefaultBilling();

        addresses.delete(address);
        addresses.flush();

        if (wasShipping || wasBilling) {
            addresses.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().findFirst().ifPresent(next -> {
                if (wasShipping) {
                    next.setDefaultShipping(true);
                }
                if (wasBilling) {
                    next.setDefaultBilling(true);
                }
            });
        }
    }

    private Address find(long userId, String publicId) {
        return addresses.findByPublicIdAndUserId(publicId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "Address not found."));
    }

    private static void apply(Address address, AddressRequest request) {
        address.setLabel(blankToNull(request.label()));
        address.setRecipientName(request.recipientName().trim());
        address.setPhone(request.phone().trim());
        address.setLine1(request.line1().trim());
        address.setLine2(blankToNull(request.line2()));
        address.setCity(request.city().trim());
        address.setState(IndianStates.canonical(request.state()).orElseThrow(() ->
                ApiException.badRequest("INVALID_STATE", "Choose a valid Indian state or union territory.")));
        address.setPincode(request.pincode().trim());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AddressResponse toResponse(Address a) {
        return new AddressResponse(a.getPublicId(), a.getLabel(), a.getRecipientName(), a.getPhone(), a.getLine1(),
                a.getLine2(), a.getCity(), a.getState(), a.getPincode(), a.isDefaultShipping(), a.isDefaultBilling());
    }
}