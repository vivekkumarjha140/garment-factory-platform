package com.garmentfactory.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.CapturingEmailSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/**
 * End-to-end authentication behaviour against a real MySQL 8.4 container and the real HTTP stack.
 * Requires Docker. Run with: mvn verify
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIT {

    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REFRESH_COOKIE = Pattern.compile("gf_refresh=([^;]*)");

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private Environment environment;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CapturingEmailSender emails;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Resp(int status, String body, List<String> setCookies) {

        String accessToken() {
            Matcher matcher = ACCESS_TOKEN.matcher(body);
            assertThat(matcher.find()).as("accessToken in %s", body).isTrue();
            return matcher.group(1);
        }

        String refreshToken() {
            for (String header : setCookies) {
                Matcher matcher = REFRESH_COOKIE.matcher(header);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            throw new AssertionError("No gf_refresh cookie in " + setCookies);
        }
    }

    private Resp send(String method, String path, String body, Map<String, String> headers) throws Exception {
        String port = environment.getProperty("local.server.port");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        headers.forEach(builder::header);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body(), response.headers().allValues("set-cookie"));
    }

    private Resp post(String path, String body) throws Exception {
        return send("POST", path, body, Map.of());
    }

    private Resp postAsBrowser(String path, String refreshToken) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Requested-With", "XMLHttpRequest");
        if (refreshToken != null) {
            headers.put("Cookie", "gf_refresh=" + refreshToken);
        }
        return send("POST", path, "", headers);
    }

    private Resp getWithBearer(String path, String accessToken) throws Exception {
        Map<String, String> headers = accessToken == null ? Map.of() : Map.of("Authorization", "Bearer " + accessToken);
        return send("GET", path, null, headers);
    }

    private static String registerJson(String email, String password) {
        return "{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String newVerifiedUser() throws Exception {
        String email = newEmail();
        assertThat(post("/api/v1/auth/register", registerJson(email, PASSWORD)).status()).isEqualTo(202);
        String token = emails.latestTokenTo(email);
        assertThat(post("/api/v1/auth/verify-email", "{\"token\":\"" + token + "\"}").status()).isEqualTo(204);
        return email;
    }

    private Resp login(String email, String password) throws Exception {
        return post("/api/v1/auth/login", loginJson(email, password));
    }

    @Test
    void registerVerifyLoginAndCallProtectedEndpoint() throws Exception {
        String email = newVerifiedUser();

        Resp login = login(email, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body()).contains("\"roles\":[\"CUSTOMER\"]");
        String cookieHeader = login.setCookies().stream().filter(c -> c.startsWith("gf_refresh=")).findFirst().orElseThrow();
        assertThat(cookieHeader).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth");

        Resp me = getWithBearer("/api/v1/users/me", login.accessToken());
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body()).contains(email);
    }

    @Test
    void loginBeforeVerificationIsForbidden() throws Exception {
        String email = newEmail();
        post("/api/v1/auth/register", registerJson(email, PASSWORD));

        Resp response = login(email, PASSWORD);

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body()).contains("EMAIL_NOT_VERIFIED");
    }

    @Test
    void wrongPasswordAndUnknownEmailLookIdentical() throws Exception {
        String email = newVerifiedUser();

        Resp wrongPassword = login(email, "Wr0ngPassword99");
        Resp unknownEmail = login(newEmail(), "Wr0ngPassword99");

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownEmail.status()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
    }

    @Test
    void duplicateRegistrationLooksIdenticalAndCreatesNoSecondAccount() throws Exception {
        String email = newVerifiedUser();

        Resp duplicate = post("/api/v1/auth/register", registerJson(email, PASSWORD));
        Resp fresh = post("/api/v1/auth/register", registerJson(newEmail(), PASSWORD));

        assertThat(duplicate.status()).isEqualTo(202).isEqualTo(fresh.status());
        assertThat(duplicate.body()).isEqualTo(fresh.body());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void weakPasswordIsRejected() throws Exception {
        Resp response = post("/api/v1/auth/register", registerJson(newEmail(), "weakpassword"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).contains("WEAK_PASSWORD");
    }

    @Test
    void refreshRotatesAndReplayRevokesTheWholeFamily() throws Exception {
        String email = newVerifiedUser();
        String first = login(email, PASSWORD).refreshToken();

        Resp rotated = postAsBrowser("/api/v1/auth/refresh", first);
        assertThat(rotated.status()).isEqualTo(200);
        String second = rotated.refreshToken();
        assertThat(second).isNotEqualTo(first);

        assertThat(postAsBrowser("/api/v1/auth/refresh", first).status()).isEqualTo(401);
        assertThat(postAsBrowser("/api/v1/auth/refresh", second).status()).isEqualTo(401);
    }

    @Test
    void refreshWithoutBrowserHeaderIsForbidden() throws Exception {
        String refresh = login(newVerifiedUser(), PASSWORD).refreshToken();

        Resp response = send("POST", "/api/v1/auth/refresh", "", Map.of("Cookie", "gf_refresh=" + refresh));

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void refreshTokenIsStoredOnlyAsHash() throws Exception {
        String refresh = login(newVerifiedUser(), PASSWORD).refreshToken();

        Integer plaintextRows = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, refresh);
        assertThat(plaintextRows).isZero();
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        String refresh = login(newVerifiedUser(), PASSWORD).refreshToken();

        assertThat(postAsBrowser("/api/v1/auth/logout", refresh).status()).isEqualTo(204);
        assertThat(postAsBrowser("/api/v1/auth/refresh", refresh).status()).isEqualTo(401);
    }

    @Test
    void passwordResetSignsOutEverywhereAndAllowsNewLogin() throws Exception {
        String email = newVerifiedUser();
        String refresh = login(email, PASSWORD).refreshToken();

        assertThat(post("/api/v1/auth/forgot-password", "{\"email\":\"" + email + "\"}").status()).isEqualTo(202);
        String resetToken = emails.latestTokenTo(email);
        String newPassword = "An0therStrongPass";

        assertThat(post("/api/v1/auth/reset-password",
                "{\"token\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\"}").status()).isEqualTo(204);

        assertThat(postAsBrowser("/api/v1/auth/refresh", refresh).status()).isEqualTo(401);
        assertThat(login(email, PASSWORD).status()).isEqualTo(401);
        assertThat(login(email, newPassword).status()).isEqualTo(200);
        assertThat(post("/api/v1/auth/reset-password",
                "{\"token\":\"" + resetToken + "\",\"newPassword\":\"Yet4notherPassword\"}").status()).isEqualTo(400);
    }

    @Test
    void forgotPasswordForUnknownEmailLooksTheSame() throws Exception {
        Resp known = post("/api/v1/auth/forgot-password", "{\"email\":\"" + newVerifiedUser() + "\"}");
        Resp unknown = post("/api/v1/auth/forgot-password", "{\"email\":\"" + newEmail() + "\"}");

        assertThat(known.status()).isEqualTo(202).isEqualTo(unknown.status());
        assertThat(known.body()).isEqualTo(unknown.body());
    }

    @Test
    void accountLocksAfterRepeatedFailuresEvenForTheCorrectPassword() throws Exception {
        String email = newVerifiedUser();

        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "Wr0ngPassword99").status()).isEqualTo(401);
        }

        assertThat(login(email, PASSWORD).status()).isEqualTo(401);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND locked_until IS NOT NULL", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void blockedUserLosesAccessImmediately() throws Exception {
        String email = newVerifiedUser();
        String accessToken = login(email, PASSWORD).accessToken();
        assertThat(getWithBearer("/api/v1/users/me", accessToken).status()).isEqualTo(200);

        jdbc.update("UPDATE users SET status = 'BLOCKED' WHERE email = ?", email);

        assertThat(getWithBearer("/api/v1/users/me", accessToken).status()).isEqualTo(401);
    }

    @Test
    void missingOrInvalidAccessTokenIsRejected() throws Exception {
        assertThat(getWithBearer("/api/v1/users/me", null).status()).isEqualTo(401);
        assertThat(getWithBearer("/api/v1/users/me", "not-a-jwt").status()).isEqualTo(401);
    }
}