package com.garmentfactory.customer.controller;

import com.garmentfactory.customer.dto.CustomerDtos.CustomerProfileResponse;
import com.garmentfactory.customer.dto.CustomerDtos.UpdateProfileRequest;
import com.garmentfactory.customer.service.CustomerProfileService;
import com.garmentfactory.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer's own profile. There is no id in the path, so nobody can ask for another customer's. */
@RestController
@RequestMapping("/api/v1/customers/me")
@Tag(name = "Customer profile")
public class CustomerProfileController {

    private final CustomerProfileService service;

    public CustomerProfileController(CustomerProfileService service) {
        this.service = service;
    }

    @GetMapping
    public CustomerProfileResponse profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(user.id());
    }

    @PatchMapping
    public CustomerProfileResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody UpdateProfileRequest request) {
        return service.update(user.id(), request);
    }
}