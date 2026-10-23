package com.garmentfactory.identity.service;

import com.garmentfactory.identity.api.UserContacts;
import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.repository.UserRepository;
import com.garmentfactory.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserContactsService implements UserContacts {

    private final UserRepository users;

    UserContactsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Contact get(long userId) {
        User user = find(userId);
        return new Contact(user.getFullName(), user.getEmail(), user.getPhone());
    }

    @Override
    @Transactional
    public void update(long userId, String fullName, String phone) {
        User user = find(userId);
        if (fullName != null) {
            user.setFullName(fullName.trim());
        }
        if (phone != null) {
            user.setPhone(phone.isBlank() ? null : phone.trim());
        }
    }

    private User find(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
    }
}