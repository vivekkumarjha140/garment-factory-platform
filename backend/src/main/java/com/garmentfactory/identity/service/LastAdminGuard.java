package com.garmentfactory.identity.service;

import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.UserStatus;
import com.garmentfactory.identity.repository.UserRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Keeps at least one active administrator. The admin rows are read with a locking read, so two
 * admins demoting or blocking each other at the same moment queue up and the second one fails,
 * instead of both succeeding on stale data. Must be called inside the changing transaction.
 */
@Component
public class LastAdminGuard {

    private static final String ADMIN = "ADMIN";

    private final UserRepository users;

    public LastAdminGuard(UserRepository users) {
        this.users = users;
    }

    public void requireAnotherActiveAdmin(Long targetUserId) {
        List<User> activeAdmins = users.lockActiveWithRole(ADMIN, UserStatus.ACTIVE);
        boolean anotherExists = activeAdmins.stream().anyMatch(admin -> !admin.getId().equals(targetUserId));
        if (!anotherExists) {
            throw ApiException.conflict("LAST_ADMIN", "At least one active administrator must remain.");
        }
    }
}