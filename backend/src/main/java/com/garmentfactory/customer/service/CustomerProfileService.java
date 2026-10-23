package com.garmentfactory.customer.service;

import com.garmentfactory.customer.dto.CustomerDtos.CustomerProfileResponse;
import com.garmentfactory.customer.dto.CustomerDtos.UpdateProfileRequest;
import com.garmentfactory.customer.entity.CustomerProfile;
import com.garmentfactory.customer.repository.CustomerProfileRepository;
import com.garmentfactory.identity.api.UserContacts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer's profile. It is created the first time it is needed, so staff accounts and existing
 * users need no migration and identity never has to know the customer module exists.
 */
@Service
public class CustomerProfileService {

    private final CustomerProfileRepository profiles;
    private final UserContacts contacts;

    public CustomerProfileService(CustomerProfileRepository profiles, UserContacts contacts) {
        this.profiles = profiles;
        this.contacts = contacts;
    }

    @Transactional
    public CustomerProfileResponse get(long userId) {
        return toResponse(userId, ensure(userId));
    }

    @Transactional
    public CustomerProfileResponse update(long userId, UpdateProfileRequest request) {
        CustomerProfile profile = lockForUser(userId);
        contacts.update(userId, request.fullName(), request.phone());
        if (request.marketingOptIn() != null) {
            profile.setMarketingOptIn(request.marketingOptIn());
        }
        return toResponse(userId, profile);
    }

    /**
     * Creates the profile if missing and locks its row until the transaction ends. Every address change
     * takes this lock first, so one customer's concurrent changes run one after another.
     */
    @Transactional
    public CustomerProfile lockForUser(long userId) {
        profiles.insertIfAbsent(userId);

        return profiles.lockByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Customer profile missing after creation"));
    }

    private CustomerProfile ensure(long userId) {
        return profiles.findByUserId(userId).orElseGet(() -> {
            profiles.insertIfAbsent(userId);
            return profiles.findByUserId(userId)
                    .orElseThrow(() -> new IllegalStateException("Customer profile missing after creation"));
        });
    }

    private CustomerProfileResponse toResponse(long userId, CustomerProfile profile) {
        UserContacts.Contact contact = contacts.get(userId);
        return new CustomerProfileResponse(contact.fullName(), contact.email(), contact.phone(),
                profile.getCustomerType().name(), profile.isMarketingOptIn());
    }
}