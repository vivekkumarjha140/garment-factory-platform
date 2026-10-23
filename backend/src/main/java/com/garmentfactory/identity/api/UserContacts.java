package com.garmentfactory.identity.api;

/**
 * What other modules may know about a user's contact details, and the only way they may change them.
 * The customer module uses this instead of touching identity's entities or tables.
 */
public interface UserContacts {

    record Contact(String fullName, String email, String phone) {
    }

    Contact get(long userId);

    /** A null argument leaves that value unchanged. For the phone, an empty string clears it. */
    void update(long userId, String fullName, String phone);
}