package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.order.service.OrderExpirySweeper;
import com.garmentfactory.payment.service.RefundService;
import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import com.garmentfactory.payment.FakeRazorpayConfig;
import com.garmentfactory.payment.FakeRazorpayConfig.FakeGateway;
import com.garmentfactory.support.OrderFixtures;
import com.garmentfactory.support.OrderFixtures.Placed;
import java.time.Duration;
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

/** Cash on delivery, cancellation, expiry, coupon release and refunds, end to end. Requires Docker. */
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
        "app.cod.enabled=true",
        "app.cod.max-order-amount=2000.00",
        "app.cod.max-open-orders=2"})
class OrderLifecycleIT {

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
    @Autowired
    private FakeGateway gateway;
    @Autowired
    private RefundService refundService;
    @Autowired
    private OrderExpirySweeper sweeper;

    private ApiClient api;
    private OrderFixtures fx;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(environment);
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson("admin@example.com", "Adm1nStrongPass"));
        assertThat(login.status()).isEqualTo(200);
        fx = new OrderFixtures(api, jdbc, emails, login.accessToken());
    }

    // ---------- helpers ----------

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

    private String reservationStatus(Placed o) {
        return jdbc.queryForObject("SELECT status FROM stock_reservations WHERE order_id = ?", String.class, o.orderId());
    }

    private void awaitRefundRow(Placed o, String status) {
        Awaitility.await().ignoreExceptions().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ? AND payment_type = 'REFUND'",
                        String.class, o.orderId())).isEqualTo(status));
    }

    private String capturedProviderPaymentId(Placed o) {
        return jdbc.queryForObject("SELECT provider_payment_id FROM payments WHERE order_id = ? AND payment_type = 'FULL'",
                String.class, o.orderId());
    }

    private long refundsOf(String providerPaymentId) {
        return gateway.refunds.stream().filter(r -> r.providerPaymentId().equals(providerPaymentId)).count();
    }

    // ---------- cash on delivery ----------

    @Test
    void aCodOrderIsConfirmedAtOnceWithStockDeductedAndNoInvoiceYet() throws Exception {
        Placed order = fx.placedOrder(2, "COD", null);

        assertThat(orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(paymentStatus(order)).isEqualTo("COD_PENDING");
        assertThat(jdbc.queryForObject("SELECT payment_method FROM orders WHERE id = ?", String.class, order.orderId())).isEqualTo("COD");
        assertThat(jdbc.queryForObject("SELECT payment_due_at FROM orders WHERE id = ?", java.sql.Timestamp.class, order.orderId())).isNull();
        assertThat(reservationStatus(order)).isEqualTo("CONSUMED");
        assertThat(onHand(order)).isEqualTo(8);

        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() ->
                emails.sentTo(order.email()).stream().anyMatch(m -> m.subject().contains(order.orderNumber())));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoices WHERE order_id = ?", Long.class, order.orderId())).isZero();
    }

    @Test
    void aCodOrderAboveTheLimitIsRefused() throws Exception {
        Response response = fx.tryPlace(5, "COD");

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body()).contains("COD_LIMIT_EXCEEDED");
    }

    @Test
    void aCustomerCannotHaveMoreOpenCodOrdersThanAllowed() throws Exception {
        Placed first = fx.placedOrder(1, "COD", null);

        assertThat(fx.placeMore(first, 1, "COD").status()).isEqualTo(201);
        Response third = fx.placeMore(first, 1, "COD");

        assertThat(third.status()).isEqualTo(422);
        assertThat(third.body()).contains("COD_OPEN_ORDERS_LIMIT");
    }

    // ---------- cancellation ----------

    @Test
    void cancellingAnUnpaidOrderReleasesItsStockAndIsRepeatable() throws Exception {
        Placed order = fx.placedOrder(2);
        assertThat(reservationStatus(order)).isEqualTo("ACTIVE");

        Response first = fx.cancel(order);
        Response second = fx.cancel(order);

        assertThat(first.status()).as(first.body()).isEqualTo(200);
        assertThat(first.body()).contains("\"orderStatus\":\"CANCELLED\"");
        assertThat(second.status()).isEqualTo(200);
        assertThat(reservationStatus(order)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = ? AND dimension = 'ORDER' AND to_status = 'CANCELLED'",
                Long.class, order.orderId())).isEqualTo(1);
    }

    @Test
    void cancellingGivesTheCouponBack() throws Exception {
        String code = fx.coupon("PERCENTAGE", "10");
        Placed order = fx.placedOrder(2, "ONLINE", code);
        assertThat(jdbc.queryForObject("SELECT times_used FROM coupons WHERE code = ?", Integer.class, code)).isEqualTo(1);

        assertThat(fx.cancel(order).status()).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT times_used FROM coupons WHERE code = ?", Integer.class, code)).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT status FROM coupon_redemptions WHERE order_id = ?", String.class, order.orderId())).isEqualTo("RELEASED");
    }

    @Test
    void cancellingAConfirmedCodOrderPutsTheStockBackWithoutARefund() throws Exception {
        Placed order = fx.placedOrder(2, "COD", null);
        assertThat(onHand(order)).isEqualTo(8);

        assertThat(fx.cancel(order).status()).isEqualTo(200);

        assertThat(orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(onHand(order)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE reference_type = 'ORDER_CANCEL' AND reference_id = ?",
                Long.class, String.valueOf(order.orderId()))).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id = ?", Long.class, order.orderId())).isZero();
    }

    @Test
    void cancellingAPaidOrderRestocksAndRefundsTheFullAmountOnce() throws Exception {
        Placed order = fx.paidOrder(2);
        assertThat(orderStatus(order)).isEqualTo("CONFIRMED");
        assertThat(onHand(order)).isEqualTo(8);
        String paymentId = capturedProviderPaymentId(order);

        assertThat(fx.cancel(order).status()).isEqualTo(200);
        assertThat(fx.cancel(order).status()).isEqualTo(200);

        assertThat(onHand(order)).isEqualTo(10);
        awaitRefundRow(order, "REFUNDED");
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> "REFUNDED".equals(paymentStatus(order)));
        assertThat(refundsOf(paymentId)).isEqualTo(1);
        assertThat(gateway.refunds.stream().filter(r -> r.providerPaymentId().equals(paymentId)).findFirst().orElseThrow().amountPaise())
                .isEqualTo(order.grandTotal().movePointRight(2).longValueExact());
    }

    @Test
    void anOrderBeingProcessedCannotBeCancelledOnline() throws Exception {
        Placed order = fx.placedOrder(1, "COD", null);
        jdbc.update("UPDATE orders SET order_status = 'PROCESSING' WHERE id = ?", order.orderId());

        Response response = fx.cancel(order);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body()).contains("ORDER_NOT_CANCELLABLE");
    }

    @Test
    void someoneElsesOrderCannotBeCancelled() throws Exception {
        Placed order = fx.placedOrder(1);
        String other = fx.registerAndLogin("other-" + java.util.UUID.randomUUID() + "@example.com");

        assertThat(fx.as(other, "POST", "/api/v1/orders/" + order.orderNumber() + "/cancel", "{}").status()).isEqualTo(404);
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    }

    // ---------- expiry ----------

    @Test
    void theSweeperCancelsAnUnpaidOrderOnceItsPaymentWindowHasEnded() throws Exception {
        Placed order = fx.placedOrder(2);
        jdbc.update("UPDATE orders SET payment_due_at = UTC_TIMESTAMP(6) - INTERVAL 1 HOUR WHERE id = ?", order.orderId());

        sweeper.sweep();

        assertThat(orderStatus(order)).isEqualTo("CANCELLED");
        assertThat(reservationStatus(order)).isIn("RELEASED", "EXPIRED");
        assertThat(jdbc.queryForObject("SELECT reason FROM order_status_history WHERE order_id = ? AND to_status = 'CANCELLED'",
                String.class, order.orderId())).contains("Payment window expired");
    }

    // ---------- refunds ----------

    @Test
    void aPaymentThatArrivesAfterTheOrderWasCancelledIsRefundedAutomatically() throws Exception {
        Placed order = fx.placedOrder(2);
        String razorpayOrder = fx.initiate(order);
        assertThat(fx.cancel(order).status()).isEqualTo(200);

        assertThat(fx.captureByWebhook(order, razorpayOrder, OrderFixtures.unique("evt_")).status()).isEqualTo(200);

        assertThat(orderStatus(order)).isEqualTo("CANCELLED");
        awaitRefundRow(order, "REFUNDED");
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> "REFUNDED".equals(paymentStatus(order)));
        assertThat(refundsOf(capturedProviderPaymentId(order))).isEqualTo(1);
    }

    @Test
    void aFailedRefundIsRetriedAndNeverIssuedTwice() throws Exception {
        Placed order = fx.paidOrder(2);
        String paymentId = capturedProviderPaymentId(order);
        gateway.failRefunds = true;
        try {
            assertThat(fx.cancel(order).status()).isEqualTo(200);
            awaitRefundRow(order, "FAILED");
            assertThat(paymentStatus(order)).isEqualTo("PAID");
        } finally {
            gateway.failRefunds = false;
        }

        assertThat(refundService.refundOrder(order.orderId())).isEqualTo(RefundService.Result.REFUNDED);
        assertThat(refundService.refundOrder(order.orderId())).isEqualTo(RefundService.Result.ALREADY_REFUNDED);

        assertThat(paymentStatus(order)).isEqualTo("REFUNDED");
        assertThat(refundsOf(paymentId)).isEqualTo(1);
    }
}