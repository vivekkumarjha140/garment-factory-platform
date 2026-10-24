package com.garmentfactory.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Rate limits against a real Redis and MySQL. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.rate-limit.enabled=true")
class RateLimitIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private Environment environment;
    @Autowired
    private StringRedisTemplate redis;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(environment);
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    private static String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private Response login(String email) throws Exception {
        return api.post("/api/v1/auth/login", ApiClient.loginJson(email, "Wr0ngPassword99"));
    }

    @Test
    void loginAttemptsPerEmailAreLimitedWithRetryAfter() throws Exception {
        String email = newEmail();
        for (int i = 0; i < 10; i++) {
            assertThat(login(email).status()).isEqualTo(401);
        }

        Response blocked = login(email);

        assertThat(blocked.status()).isEqualTo(429);
        assertThat(Long.parseLong(blocked.header("Retry-After"))).isPositive();
    }

    @Test
    void unknownEmailsAreLimitedExactlyLikeKnownOnes() throws Exception {
        String unknown = newEmail();
        for (int i = 0; i < 10; i++) {
            login(unknown);
        }

        assertThat(login(unknown).status()).isEqualTo(429);
    }

    @Test
    void registrationIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(api.post("/api/v1/auth/register", ApiClient.registerJson(newEmail(), "Str0ngPassw0rd")).status())
                    .isEqualTo(202);
        }

        Response blocked = api.post("/api/v1/auth/register", ApiClient.registerJson(newEmail(), "Str0ngPassw0rd"));

        assertThat(blocked.status()).isEqualTo(429);
    }

    @Test
    void forgotPasswordIsLimitedPerEmailWithoutAffectingOthers() throws Exception {
        String email = newEmail();
        for (int i = 0; i < 3; i++) {
            assertThat(api.post("/api/v1/auth/forgot-password", "{\"email\":\"" + email + "\"}").status()).isEqualTo(202);
        }

        assertThat(api.post("/api/v1/auth/forgot-password", "{\"email\":\"" + email + "\"}").status()).isEqualTo(429);
        assertThat(api.post("/api/v1/auth/forgot-password", "{\"email\":\"" + newEmail() + "\"}").status()).isEqualTo(202);
    }
}