package com.garmentfactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Starts the full application against a real MySQL 8.4 container and verifies that
 * migrations, schema validation, health probes and the locked-down security chain work.
 * Requires Docker. Run with: mvn verify
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InfrastructureIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbc;

    private HttpResponse<String> get(String path) throws Exception {
        String port = environment.getProperty("local.server.port");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }


    @Test
    void flywayAppliesAllMigrations() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class);
        Integer failed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0",
                Integer.class);
        Integer settings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_settings",
                Integer.class);

        assertThat(applied).isGreaterThanOrEqualTo(21);
        assertThat(failed).isZero();
        assertThat(settings).isEqualTo(3);
    }


    @Test
    void checkConstraintsAreEnforcedByMysql() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO number_sequences (seq_key, next_value) VALUES ('BAD', 0)"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void readinessProbeIsUpWhenDatabaseIsReachable() throws Exception {
        HttpResponse<String> response = get("/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void pingEndpointIsPublic() throws Exception {
        HttpResponse<String> response = get("/api/v1/system/ping");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("garment-factory-platform");
    }

    @Test
    void everythingElseRejectsAnonymousCallers() throws Exception {
        HttpResponse<String> response = get("/api/v1/orders");

        assertThat(response.statusCode()).isEqualTo(401);
    }
}