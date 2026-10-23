package com.garmentfactory.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.identity.dto.AuthDtos.ClientInfo;
import com.garmentfactory.identity.entity.RefreshToken;
import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.UserStatus;
import com.garmentfactory.identity.repository.RefreshTokenRepository;
import com.garmentfactory.identity.service.RefreshTokenService;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.security.Tokens;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final String RAW = "raw-token-value";
    private static final String FAMILY = "family-1";
    private static final ClientInfo CLIENT = new ClientInfo("127.0.0.1", "JUnit");

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private AuditRecorder audit;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, TestProperties.appProperties(), Clock.fixed(NOW, ZoneOffset.UTC), audit);
    }

    private User user(UserStatus status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setPublicId("public-7");
        user.setStatus(status);
        return user;
    }

    private RefreshToken storedToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setFamilyId(FAMILY);
        token.setTokenHash(Tokens.sha256Hex(RAW));
        token.setExpiresAt(NOW.plusSeconds(3600));
        when(repository.findByTokenHash(Tokens.sha256Hex(RAW))).thenReturn(Optional.of(token));
        return token;
    }

    private void assertUnauthorized(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void validTokenIsRotatedWithinTheSameFamily() {
        RefreshToken current = storedToken(user(UserStatus.ACTIVE));
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.Rotation rotation = service.rotate(RAW, CLIENT);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(FAMILY);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(Tokens.sha256Hex(rotation.token().rawToken()));
        assertThat(rotation.token().rawToken()).isNotEqualTo(RAW);
        assertThat(current.getRotatedAt()).isEqualTo(NOW);
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void reusedTokenRevokesTheWholeFamily() {
        RefreshToken current = storedToken(user(UserStatus.ACTIVE));
        current.setRotatedAt(NOW.minusSeconds(5));

        assertUnauthorized(() -> service.rotate(RAW, CLIENT));

        verify(repository).revokeFamily(FAMILY, NOW);
        verify(repository, never()).save(any());
        verify(audit).record(any());
    }

    @Test
    void revokedTokenIsRejected() {
        RefreshToken current = storedToken(user(UserStatus.ACTIVE));
        current.setRevokedAt(NOW.minusSeconds(60));

        assertUnauthorized(() -> service.rotate(RAW, CLIENT));

        verify(repository, never()).revokeFamily(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void expiredTokenIsRejected() {
        RefreshToken current = storedToken(user(UserStatus.ACTIVE));
        current.setExpiresAt(NOW.minusSeconds(1));

        assertUnauthorized(() -> service.rotate(RAW, CLIENT));

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void blockedUserCannotRefreshAndLosesTheFamily() {
        storedToken(user(UserStatus.BLOCKED));

        assertUnauthorized(() -> service.rotate(RAW, CLIENT));

        verify(repository).revokeFamily(FAMILY, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenHash(Tokens.sha256Hex(RAW))).thenReturn(Optional.empty());

        assertUnauthorized(() -> service.rotate(RAW, CLIENT));

        verify(repository, never()).save(any());
    }
}