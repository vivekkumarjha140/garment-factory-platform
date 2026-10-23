package com.garmentfactory.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requests and responses of the signed-in customer's own profile and address book. */
public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerProfileResponse(String fullName, String email, String phone,
                                          String customerType, boolean marketingOptIn) {
    }

    /** PATCH semantics: a missing (null) field is left unchanged. An empty phone clears it. */
    public record UpdateProfileRequest(
            @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 120) String fullName,
            @Pattern(regexp = "^(\\+?[0-9]{7,15})?$", message = "must be 7 to 15 digits, optionally starting with +") String phone,
            Boolean marketingOptIn) {
    }

    /**
     * defaultShipping and defaultBilling only ever mean "make this the default" when true. A default
     * cannot be switched off directly: make another address the default instead.
     */
    public record AddressRequest(
            @Size(max = 30) String label,
            @NotBlank @Size(max = 120) String recipientName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7 to 15 digits, optionally starting with +") String phone,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 60) String state,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a valid 6-digit pincode") String pincode,
            Boolean defaultShipping,
            Boolean defaultBilling) {
    }

    public record AddressResponse(String id, String label, String recipientName, String phone, String line1,
                                  String line2, String city, String state, String pincode,
                                  boolean defaultShipping, boolean defaultBilling) {
    }
}