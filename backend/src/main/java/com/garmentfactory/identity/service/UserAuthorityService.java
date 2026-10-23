package com.garmentfactory.identity.service;

import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.repository.UserRepository;
import com.garmentfactory.shared.security.AuthenticatedUser;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the principal (roles and permissions) from the current database state. */
@Service
public class UserAuthorityService {

    private final UserRepository users;

    public UserAuthorityService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser principalFor(User user) {
        Set<String> roles = Set.copyOf(users.findRoleCodes(user.getId()));
        Set<String> permissions = Set.copyOf(users.findPermissionCodes(user.getId()));
        return new AuthenticatedUser(user.getId(), user.getPublicId(), user.getEmail(), user.getFullName(), roles, permissions);
    }
}