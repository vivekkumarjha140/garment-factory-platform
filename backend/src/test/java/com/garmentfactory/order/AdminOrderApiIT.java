package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.BytesResponse;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import com.garmentfactory.payment.FakeRazorpayConfig;
import com.garmentfactory.support.OrderFixtures;
import com.garmentfactory.support.OrderFixtures.Placed;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
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

/** Staff order management: permissions, search, fulfilment, cash on delivery, staff cancel. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@Import(FakeRazorpayConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin",
        "app.razorpay.key-id=rzp_test_key",
        "app.razorpay.key-secret=test_key_secret",
        "app.razorpay.webhook-secret=whsec_test_secret",
        "app.cod.max-order-amount=5000.00"})
class AdminOrderApiIT {

    private static final String ADMIN_ORDERS = "/api/v1/admin/orders";
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

    private ApiClient api;
    private OrderFixtures fx;
    private String dispatch;
    private String sales;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(environment);
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson("admin@example.com", "Adm1nStrongPass"));
        assertThat(login.status()).isEqualTo(200);
        fx = new OrderFixtures(api, jdbc, emails, login.accessToken());
        dispatch = fx.newStaff("DISPATCH_MANAGER");   // view + fulfil
        sales = fx.newStaff("SALES_MANAGER");         // view + cancel
    }

    // ---------- helpers ----------

    private Response act(String token, Placed order, String action, String body) throws Exception {
        return fx.as(token, "POST", ADMIN_ORDERS + "/" + order.orderNumber() + "/" + action, body);
    }

    private static List<String> orderNumbers(Response response) {
        List<String> found = new ArrayList<>();
        Matcher matcher = ORDER_NUMBER.matcher(response.body());
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private void processToShipped(Placed order) throws Exception {
        assertThat(act(dispatch, order, "start-processing", "{}").status()).isEqualTo(200);
        assertThat(act(dispatch, order, "pack", "{}").status()).isEqualTo(200);
        assertThat(act(dispatch, order, "ship", "{}").status()).isEqualTo(200);
    }

    private String orderStatus(Placed o) {
        return jdbc.queryForObject("SELECT order_status FROM orders WHERE id = ?", String.class, o.orderId());
    }

    private String paymentStatus(Placed o) {
        return jdbc.queryForObject("SELECT payment_status FROM orders WHERE id = ?", String.class, o.orderId());
    }

    private int onHand(Placed o) {
        return jdbc.queryForObject("""
                SELECT on_hand FROM finished_goods_stock
                 WHERE variant_id = (SELECT id FROM product_variants WHERE public_id = ?)
                """, Integer.class, o.variantId());
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // ---------- permissions ----------

    @Test
    void onlyStaffWithTheRightPermissionCanReadOrActOnOrders() throws Exception {
        Placed order = fx.paidOrder(1);
        String customer = order.token();
        String inspector = fx.newStaff("QC_INSPECTOR");   // holds no order permission

        assertThat(api.get(ADMIN_ORDERS, null).status()).isEqualTo(401);
        assertThat(fx.as(customer, "GET", ADMIN_ORDERS, null).status()).isEqualTo(403);
        assertThat(fx.as(inspector, "GET", ADMIN_ORDERS, null).status()).isEqualTo(403);
        assertThat(fx.as(sales, "GET", ADMIN_ORDERS, null).status()).isEqualTo(200);
        assertThat(act(sales, order, "start-processing", "{}").status()).isEqualTo(403);        // view + cancel, not fulfil
        assertThat(act(dispatch, order, "cancel", "{\"reason\":\"x\"}").status()).isEqualTo(403);  // view + fulfil, not cancel
        assertThat(act(customer, order, "start-processing", "{}").status()).isEqualTo(403);
    }

    // ---------- reading ----------

    @Test
    void ordersCanBeSearchedFilteredAndOpened() throws Exception {
        Placed online = fx.paidOrder(1);
        Placed cod = fx.placedOrder(1, "COD", null);

        Response byNumber = fx.as(sales, "GET", ADMIN_ORDERS + "?q=" + online.orderNumber(), null);
        assertThat(orderNumbers(byNumber)).containsExactly(online.orderNumber());
        assertThat(byNumber.body()).contains("\"totalElements\":1").contains("\"customerEmail\":\"" + online.email() + "\"");

        Response byMethod = fx.as(sales, "GET", ADMIN_ORDERS + "?paymentMethod=COD&q=" + cod.orderNumber(), null);
        assertThat(orderNumbers(byMethod)).containsExactly(cod.orderNumber());
        assertThat(orderNumbers(fx.as(sales, "GET", ADMIN_ORDERS + "?paymentMethod=ONLINE&q=" + cod.orderNumber(), null))).isEmpty();
        assertThat(orderNumbers(fx.as(sales, "GET", ADMIN_ORDERS + "?orderStatus=CANCELLED&q=" + online.orderNumber(), null))).isEmpty();
        assertThat(orderNumbers(fx.as(sales, "GET", ADMIN_ORDERS + "?q=%25", null))).isEmpty();   // a typed % is not a wildcard

        Response detail = fx.as(sales, "GET", ADMIN_ORDERS + "/" + online.orderNumber(), null);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body()).contains("\"customer\":{").contains(online.email()).contains("\"invoiceNumber\"");
        assertThat(fx.as(sales, "GET", ADMIN_ORDERS + "/GF-0000-000000", null).status()).isEqualTo(404);
    }

    // ---------- fulfilment ----------

    @Test
    void anOnlineOrderIsTakenFromConfirmedToCompletedAndTheCustomerIsTold() throws Exception {
        Placed order = fx.paidOrder(2);

        Response started = act(dispatch, order, "start-processing", "{}");
        assertThat(started.status()).as(started.body()).isEqualTo(200);
        assertThat(started.body()).contains("\"orderStatus\":\"PROCESSING\"");
        assertThat(act(dispatch, order, "pack", "{}").body()).contains("\"shipmentStatus\":\"PACKED\"");
        assertThat(act(dispatch, order, "ship", "{}").body()).contains("\"shipmentStatus\":\"SHIPPED\"");
        Response delivered = act(dispatch, order, "deliver", "{\"cashCollected\":false}");

        assertThat(delivered.status()).as(delivered.body()).isEqualTo(200);
        assertThat(delivered.body()).contains("\"orderStatus\":\"COMPLETED\"").contains("\"shipmentStatus\":\"DELIVERED\"");
        assertThat(count("SELECT COUNT(*) FROM order_status_history WHERE order_id = ? AND actor_user_id IS NOT NULL AND to_status IN ('PACKED','SHIPPED','DELIVERED')",
                order.orderId())).isEqualTo(3);
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> emails.sentTo(order.email()).stream()
                .anyMatch(m -> m.subject().contains(order.orderNumber()) && m.subject().contains("has shipped")));
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> emails.sentTo(order.email()).stream()
                .anyMatch(m -> m.subject().contains(order.orderNumber()) && m.subject().contains("was delivered")));
    }


    @Test
    void stepsOutOfOrderAreRefusedAndRepeatingAStepChangesNothing() throws Exception {
        Placed order = fx.paidOrder(1);

        assertThat(act(dispatch, order, "pack", "{}").status()).isEqualTo(409);
        assertThat(act(dispatch, order, "ship", "{}").status()).isEqualTo(409);
        assertThat(act(dispatch, order, "start-processing", "{}").status()).isEqualTo(200);
        assertThat(act(dispatch, order, "start-processing", "{}").status()).isEqualTo(200);
        assertThat(act(dispatch, order, "ship", "{}").body())
                .contains("ORDER_ACTION_NOT_ALLOWED"); // still has to be packed

        Response deliveryAttempt =
                act(dispatch, order, "deliver", "{\"cashCollected\":false}");

        assertThat(deliveryAttempt.status())
                .as("deliver response body: " + deliveryAttempt.body())
                .isEqualTo(409);

        assertThat(count(
                "SELECT COUNT(*) FROM order_status_history WHERE order_id = ? AND to_status = 'PROCESSING'",
                order.orderId()))
                .isEqualTo(1);
    }


    @Test
    void anUnpaidOnlineOrderCannotBeProcessed() throws Exception {
        Placed unpaid = fx.placedOrder(1);

        assertThat(act(dispatch, unpaid, "start-processing", "{}").status()).isEqualTo(409);
    }

    // ---------- cash on delivery ----------

    @Test
    void aCodOrderNeedsItsCashConfirmedAndThenIsPaidAndInvoiced() throws Exception {
        Placed order = fx.placedOrder(2, "COD", null);
        processToShipped(order);

        Response withoutCash = act(dispatch, order, "deliver", "{\"cashCollected\":false}");
        assertThat(withoutCash.status()).isEqualTo(422);
        assertThat(withoutCash.body()).contains("COD_CASH_REQUIRED");
        assertThat(orderStatus(order)).isEqualTo("PROCESSING");

        Response delivered = act(dispatch, order, "deliver", "{\"cashCollected\":true}");

        assertThat(delivered.status()).as(delivered.body()).isEqualTo(200);
        assertThat(orderStatus(order)).isEqualTo("COMPLETED");
        assertThat(paymentStatus(order)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT amount_paid FROM orders WHERE id = ?", BigDecimal.class, order.orderId()))
                .isEqualByComparingTo(order.grandTotal());
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(count("SELECT COUNT(*) FROM payments WHERE order_id = ? AND payment_type = 'COD' AND status = 'CAPTURED'", order.orderId())).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM invoices WHERE order_id = ?", order.orderId())).isEqualTo(1);
        });
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'ORDER_COD_COLLECTED' AND entity_id = ?", order.orderNumber())).isEqualTo(1);
    }

    // ---------- cancelling ----------

    @Test
    void staffCancellingAPaidOrderInProcessingRestocksAndRefunds() throws Exception {
        Placed order = fx.paidOrder(2);
        assertThat(act(dispatch, order, "start-processing", "{}").status()).isEqualTo(200);
        assertThat(onHand(order)).isEqualTo(8);

        assertThat(act(sales, order, "cancel", "{\"reason\":\"customer called\"}").status()).isEqualTo(200);

        assertThat(orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(onHand(order)).isEqualTo(10);
        Awaitility.await().ignoreExceptions().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ? AND payment_type = 'REFUND'", String.class, order.orderId())).isEqualTo("REFUNDED");
            assertThat(paymentStatus(order)).isEqualTo("REFUNDED");
        });
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'ORDER_CANCELLED_BY_STAFF' AND entity_id = ?", order.orderNumber())).isEqualTo(1);
    }

    @Test
    void staffCancelNeedsAReasonAndIsRefusedOncePacked() throws Exception {
        Placed order = fx.paidOrder(1);
        assertThat(act(sales, order, "cancel", "{\"reason\":\"\"}").status()).isEqualTo(400);

        assertThat(act(dispatch, order, "start-processing", "{}").status()).isEqualTo(200);
        assertThat(act(dispatch, order, "pack", "{}").status()).isEqualTo(200);
        Response late = act(sales, order, "cancel", "{\"reason\":\"too late\"}");

        assertThat(late.status()).isEqualTo(409);
        assertThat(late.body()).contains("ORDER_NOT_CANCELLABLE");
        assertThat(orderStatus(order)).isEqualTo("PROCESSING");
    }

    // ---------- invoice ----------

    @Test
    void staffCanDownloadTheInvoiceOfAPaidOrderButNotOfAnUnpaidOne() throws Exception {
        Placed paid = fx.paidOrder(1);
        Placed unpaid = fx.placedOrder(1);

        BytesResponse pdf = api.getBytes(ADMIN_ORDERS + "/" + paid.orderNumber() + "/invoice", sales);
        BytesResponse none = api.getBytes(ADMIN_ORDERS + "/" + unpaid.orderNumber() + "/invoice", sales);

        assertThat(pdf.status()).isEqualTo(200);
        assertThat(new String(pdf.body(), 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        assertThat(none.status()).isEqualTo(409);
    }
}