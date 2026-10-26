package com.garmentfactory.customer.api;

import java.util.Optional;

public interface ShippingAddressLookup {

    /** Only addresses owned by this user are found; someone else's address is simply absent. */
    Optional<ShippingTarget> findForUser(long userId, String addressPublicId);

    Optional<ShippingTarget> findDefaultShipping(long userId);
}