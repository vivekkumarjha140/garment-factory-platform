
package com.garmentfactory.inventory.config;

import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the logged-in staff user's id for ledger, receipt and audit entries.
 */
@Component
public class InventoryActorResolver {

    public Long currentUserId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }

        throw ApiException.unauthorized(
                "UNAUTHENTICATED",
                "Authentication is required"
        );
    }
}
