package com.garmentfactory.customer.api;

import java.util.Optional;

public interface CheckoutAddressLookup {

    /** Only addresses owned by this user are found; someone else's address is simply absent. */
    Optional<CheckoutAddress> findForUser(long userId, String addressPublicId);
}