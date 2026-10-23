package com.garmentfactory.customer.controller;

import com.garmentfactory.customer.dto.CustomerDtos.AddressRequest;
import com.garmentfactory.customer.dto.CustomerDtos.AddressResponse;
import com.garmentfactory.customer.service.AddressService;
import com.garmentfactory.customer.service.AddressService.DefaultKind;
import com.garmentfactory.customer.service.IndianStates;
import com.garmentfactory.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customer addresses")
public class AddressController {

    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    @GetMapping("/reference/states")
    public List<String> states() {
        return IndianStates.all();
    }

    @GetMapping("/me/addresses")
    public List<AddressResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.id());
    }

    @PostMapping("/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody AddressRequest request) {
        return service.create(user.id(), request);
    }

    @PutMapping("/me/addresses/{id}")
    public AddressResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id,
                                  @Valid @RequestBody AddressRequest request) {
        return service.update(user.id(), id, request);
    }

    @DeleteMapping("/me/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        service.delete(user.id(), id);
    }

    @PostMapping("/me/addresses/{id}/default-shipping")
    public AddressResponse defaultShipping(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return service.makeDefault(user.id(), id, DefaultKind.SHIPPING);
    }

    @PostMapping("/me/addresses/{id}/default-billing")
    public AddressResponse defaultBilling(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id) {
        return service.makeDefault(user.id(), id, DefaultKind.BILLING);
    }
}