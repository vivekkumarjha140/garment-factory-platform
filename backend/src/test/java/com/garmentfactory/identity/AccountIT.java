package com.garmentfactory.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Sessions, change-password and audit trail against a real MySQL 8.4. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountIT {

    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final Pattern OTHER_SESSION_ID = Pattern.compile("\\{\"id\":\"([^\"]+)\"[^}]*\"current\":false");

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--log-bin-trust-function-creators=1");

    @Autowired
    private Environment environment;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CapturingEmailSender emails;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(environment);
    }

    private String newVerifiedUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        assertThat(api.post("/api/v1/auth/register", ApiClient.registerJson(email, PASSWORD)).status()).isEqualTo(202);
        String token = emails.latestTokenTo(email);
        assertThat(api.post("/api/v1/auth/verify-email", "{\"token\":\"" + token + "\"}").status()).isEqualTo(204);
        return email;
    }

    private Response login(String email, String password) throws Exception {
        return api.post("/api/v1/auth/login", ApiClient.loginJson(email, password));
    }

    private Response sessions(Response login) throws Exception {
        return api.sendWithSession("GET", "/api/v1/auth/sessions", null, login);
    }

    private static int occurrences(String text, String needle) {
        return text.split(Pattern.quote(needle), -1).length - 1;
    }

    private int auditCount(String action, String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = ? "
                + "AND entity_id = (SELECT public_id FROM users WHERE email = ?)", Integer.class, action, email);
    }

    @Test
    void listsActiveSessionsAndMarksTheCurrentOne() throws Exception {
        String email = newVerifiedUser();
        Response first = login(email, PASSWORD);
        login(email, PASSWORD);

        Response list = sessions(first);

        assertThat(list.status()).isEqualTo(200);
        assertThat(occurrences(list.body(), "\"id\"")).isEqualTo(2);
        assertThat(occurrences(list.body(), "\"current\":true")).isEqualTo(1);
    }

    @Test
    void revokingASessionEndsOnlyThatSession() throws Exception {
        String email = newVerifiedUser();
        Response first = login(email, PASSWORD);
        Response second = login(email, PASSWORD);
        Matcher other = OTHER_SESSION_ID.matcher(sessions(first).body());
        assertThat(other.find()).isTrue();

        Response revoke = api.delete("/api/v1/auth/sessions/" + other.group(1), first.accessToken());

        assertThat(revoke.status()).isEqualTo(204);
        assertThat(api.postAsBrowser("/api/v1/auth/refresh", second.refreshToken()).status()).isEqualTo(401);
        assertThat(api.postAsBrowser("/api/v1/auth/refresh", first.refreshToken()).status()).isEqualTo(200);
    }

    @Test
    void cannotRevokeAnotherUsersSession() throws Exception {
        Response owner = login(newVerifiedUser(), PASSWORD);
        Matcher session = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(sessions(owner).body());
        assertThat(session.find()).isTrue();
        Response intruder = login(newVerifiedUser(), PASSWORD);

        Response attempt = api.delete("/api/v1/auth/sessions/" + session.group(1), intruder.accessToken());

        assertThat(attempt.status()).isEqualTo(404);
        assertThat(api.postAsBrowser("/api/v1/auth/refresh", owner.refreshToken()).status()).isEqualTo(200);
    }

    @Test
    void sessionEndpointsRequireAuthentication() throws Exception {
        assertThat(api.get("/api/v1/auth/sessions", null).status()).isEqualTo(401);
        assertThat(api.post("/api/v1/auth/logout-all", "").status()).isEqualTo(401);
    }

    @Test
    void logoutAllEndsEverySession() throws Exception {
        String email = newVerifiedUser();
        Response first = login(email, PASSWORD);
        Response second = login(email, PASSWORD);

        assertThat(api.sendWithSession("POST", "/api/v1/auth/logout-all", "", first).status()).isEqualTo(204);

        assertThat(api.postAsBrowser("/api/v1/auth/refresh", first.refreshToken()).status()).isEqualTo(401);
        assertThat(api.postAsBrowser("/api/v1/auth/refresh", second.refreshToken()).status()).isEqualTo(401);
        assertThat(auditCount("ALL_SESSIONS_REVOKED", email)).isEqualTo(1);
    }

    @Test
    void changePasswordKeepsTheCurrentSessionAndEndsTheOthers() throws Exception {
        String email = newVerifiedUser();
        Response current = login(email, PASSWORD);
        Response other = login(email, PASSWORD);
        String newPassword = "An0therStrongPass";

        Response change = api.sendWithSession("POST", "/api/v1/auth/change-password",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + newPassword + "\"}", current);

        assertThat(change.status()).isEqualTo(204);
        assertThat(api.postAsBrowser("/api/v1/auth/refresh", current.refreshToken()).status()).isEqualTo(200);
        assertThat(api.postAsBrowser("/api/v1/auth/refresh", other.refreshToken()).status()).isEqualTo(401);
        assertThat(login(email, PASSWORD).status()).isEqualTo(401);
        assertThat(login(email, newPassword).status()).isEqualTo(200);
        assertThat(auditCount("PASSWORD_CHANGED", email)).isEqualTo(1);
        assertThat(emails.sentTo(email).getLast().subject()).contains("password was changed");
    }

    @Test
    void changePasswordRejectsAWrongCurrentPassword() throws Exception {
        Response session = login(newVerifiedUser(), PASSWORD);

        Response change = api.sendWithSession("POST", "/api/v1/auth/change-password",
                "{\"currentPassword\":\"Wr0ngPassword99\",\"newPassword\":\"An0therStrongPass\"}", session);

        assertThat(change.status()).isEqualTo(400);
        assertThat(change.body()).contains("INVALID_CURRENT_PASSWORD");
    }

    @Test
    void securityEventsAreWrittenToTheAuditLog() throws Exception {
        String reuseUser = newVerifiedUser();
        String stolen = login(reuseUser, PASSWORD).refreshToken();
        api.postAsBrowser("/api/v1/auth/refresh", stolen);
        api.postAsBrowser("/api/v1/auth/refresh", stolen);

        String lockedUser = newVerifiedUser();
        for (int i = 0; i < 5; i++) {
            login(lockedUser, "Wr0ngPassword99");
        }

        assertThat(auditCount("REFRESH_TOKEN_REUSE", reuseUser)).isEqualTo(1);
        assertThat(auditCount("ACCOUNT_LOCKED", lockedUser)).isEqualTo(1);
    }
}