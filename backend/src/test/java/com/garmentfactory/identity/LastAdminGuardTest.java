package com.garmentfactory.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.UserStatus;
import com.garmentfactory.identity.repository.UserRepository;
import com.garmentfactory.identity.service.LastAdminGuard;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LastAdminGuardTest {

    @Mock
    private UserRepository users;

    private static User admin(long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void passesWhenAnotherActiveAdminExists() {
        when(users.lockActiveWithRole("ADMIN", UserStatus.ACTIVE)).thenReturn(List.of(admin(1), admin(2)));

        assertThatCode(() -> new LastAdminGuard(users).requireAnotherActiveAdmin(1L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenTheTargetIsTheOnlyActiveAdmin() {
        when(users.lockActiveWithRole("ADMIN", UserStatus.ACTIVE)).thenReturn(List.of(admin(1)));

        assertThatThrownBy(() -> new LastAdminGuard(users).requireAnotherActiveAdmin(1L))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("LAST_ADMIN");
                });
    }
}