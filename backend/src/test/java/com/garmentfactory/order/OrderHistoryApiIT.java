package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import com.garmentfactory.payment.FakeRazorpayConfig;
import com.garmentfactory.support.OrderFixtures;
import com.garmentfactory.support.OrderFixtures.Placed;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** The customer's order history list. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@Import(FakeRazorpayConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin",
        "app.razorpay.key-id=rzp_test_key",
        "app.razorpay.key-secret=test_key_secret",
        "app.razorpay.webhook-secret=whsec_test_secret"})
class OrderHistoryApiIT {

    private static final Pattern ORDER_NUMBER = Pattern.compile("\"orderNumber\":\"([^\"]+)\"");

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

    private OrderFixtures fx;

    @BeforeEach
    void setUp() throws Exception {
        ApiClient api = new ApiClient(environment);
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson("admin@example.com", "Adm1nStrongPass"));
        assertThat(login.status()).isEqualTo(200);
        fx = new OrderFixtures(api, jdbc, emails, login.accessToken());
    }

    private static List<String> orderNumbers(Response response) {
        List<String> found = new ArrayList<>();
        Matcher matcher = ORDER_NUMBER.matcher(response.body());
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    void listsTheCustomersOrdersNewestFirstWithPaging() throws Exception {
        Placed first = fx.placedOrder(1);
        String second = orderNumbers(fx.placeMore(first, 1, "ONLINE")).get(0);
        String third = orderNumbers(fx.placeMore(first, 1, "ONLINE")).get(0);

        Response page0 = fx.as(first.token(), "GET", "/api/v1/orders?page=0&size=2", null);
        Response page1 = fx.as(first.token(), "GET", "/api/v1/orders?page=1&size=2", null);

        assertThat(page0.status()).isEqualTo(200);
        assertThat(orderNumbers(page0)).containsExactly(third, second);
        assertThat(page0.body()).contains("\"totalElements\":3").contains("\"totalPages\":2").contains("\"itemCount\":1");
        assertThat(orderNumbers(page1)).containsExactly(first.orderNumber());
    }

    @Test
    void neverListsSomeoneElsesOrders() throws Exception {
        Placed mine = fx.placedOrder(1);
        Placed theirs = fx.placedOrder(1);

        Response list = fx.as(mine.token(), "GET", "/api/v1/orders", null);

        assertThat(orderNumbers(list)).containsExactly(mine.orderNumber()).doesNotContain(theirs.orderNumber());
    }

    @Test
    void aHugePageSizeIsClamped() throws Exception {
        Placed order = fx.placedOrder(1);

        Response list = fx.as(order.token(), "GET", "/api/v1/orders?size=1000", null);

        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains("\"size\":50");
    }
}