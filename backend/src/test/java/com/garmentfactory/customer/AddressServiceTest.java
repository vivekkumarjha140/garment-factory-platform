package com.garmentfactory.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garmentfactory.customer.dto.CustomerDtos.AddressRequest;
import com.garmentfactory.customer.dto.CustomerDtos.AddressResponse;
import com.garmentfactory.customer.entity.Address;
import com.garmentfactory.customer.repository.AddressRepository;
import com.garmentfactory.customer.service.AddressService;
import com.garmentfactory.customer.service.AddressService.DefaultKind;
import com.garmentfactory.customer.service.CustomerProfileService;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    private static final long USER = 7L;

    @Mock
    private AddressRepository addresses;
    @Mock
    private CustomerProfileService profiles;

    private AddressService service;

    @BeforeEach
    void setUp() {
        service = new AddressService(addresses, profiles);
    }

    private static AddressRequest request(String state, Boolean defaultShipping, Boolean defaultBilling) {
        return new AddressRequest("Home", "Asha Verma", "+919876543210", "12 MI Road", null, "Jaipur",
                state, "302001", defaultShipping, defaultBilling);
    }

    private void saveReturnsItsArgument() {
        when(addresses.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Address owned(boolean defaultShipping, boolean defaultBilling) {
        Address address = new Address();
        address.setPublicId("a1");
        address.setUserId(USER);
        address.setDefaultShipping(defaultShipping);
        address.setDefaultBilling(defaultBilling);
        return address;
    }

    private static void assertStatus(Runnable action, HttpStatus status, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(status);
            assertThat(ex.getCode()).isEqualTo(code);
        });
    }

    @Test
    void theFirstAddressBecomesTheDefaultForShippingAndBilling() {
        when(addresses.countByUserId(USER)).thenReturn(0L);
        saveReturnsItsArgument();

        AddressResponse created = service.create(USER, request("Rajasthan", null, null));

        assertThat(created.defaultShipping()).isTrue();
        assertThat(created.defaultBilling()).isTrue();
        verify(addresses).clearDefaultShipping(USER);
        verify(addresses).clearDefaultBilling(USER);
    }

    @Test
    void laterAddressesAreNotDefaultUnlessAsked() {
        when(addresses.countByUserId(USER)).thenReturn(2L);
        saveReturnsItsArgument();

        AddressResponse created = service.create(USER, request("Rajasthan", null, null));

        assertThat(created.defaultShipping()).isFalse();
        assertThat(created.defaultBilling()).isFalse();
        verify(addresses, never()).clearDefaultShipping(any());
        verify(addresses, never()).clearDefaultBilling(any());
    }

    @Test
    void anExplicitDefaultReplacesTheOldOne() {
        when(addresses.countByUserId(USER)).thenReturn(2L);
        saveReturnsItsArgument();

        AddressResponse created = service.create(USER, request("Rajasthan", true, null));

        assertThat(created.defaultShipping()).isTrue();
        assertThat(created.defaultBilling()).isFalse();
        verify(addresses).clearDefaultShipping(USER);
    }

    @Test
    void theEleventhAddressIsRejected() {
        when(addresses.countByUserId(USER)).thenReturn((long) AddressService.MAX_ADDRESSES);

        assertStatus(() -> service.create(USER, request("Rajasthan", null, null)), HttpStatus.CONFLICT, "ADDRESS_LIMIT_REACHED");

        verify(addresses, never()).save(any());
    }

    @Test
    void anUnknownStateIsRejectedAndAKnownOneIsStoredWithItsOfficialSpelling() {
        when(addresses.countByUserId(USER)).thenReturn(1L);
        saveReturnsItsArgument();

        assertStatus(() -> service.create(USER, request("Atlantis", null, null)), HttpStatus.BAD_REQUEST, "INVALID_STATE");
        assertThat(service.create(USER, request("rajasthan", null, null)).state()).isEqualTo("Rajasthan");
    }

    @Test
    void anotherCustomersAddressIsNotFoundForEveryOperation() {
        when(addresses.findByPublicIdAndUserId("a1", USER)).thenReturn(Optional.empty());

        assertStatus(() -> service.update(USER, "a1", request("Goa", null, null)), HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND");
        assertStatus(() -> service.delete(USER, "a1"), HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND");
        assertStatus(() -> service.makeDefault(USER, "a1", DefaultKind.SHIPPING), HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND");
    }

    @Test
    void deletingTheDefaultPromotesTheNewestRemainingAddress() {
        Address deleted = owned(true, true);
        Address next = new Address();
        when(addresses.findByPublicIdAndUserId("a1", USER)).thenReturn(Optional.of(deleted));
        when(addresses.findByUserIdOrderByCreatedAtDescIdDesc(USER)).thenReturn(List.of(next));

        service.delete(USER, "a1");

        verify(addresses).delete(deleted);
        verify(addresses).flush();
        assertThat(next.isDefaultShipping()).isTrue();
        assertThat(next.isDefaultBilling()).isTrue();
    }

    @Test
    void makingTheCurrentDefaultTheDefaultAgainChangesNothing() {
        Address current = owned(true, false);
        when(addresses.findByPublicIdAndUserId("a1", USER)).thenReturn(Optional.of(current));

        service.makeDefault(USER, "a1", DefaultKind.SHIPPING);

        verify(addresses, never()).clearDefaultShipping(any());
    }
}