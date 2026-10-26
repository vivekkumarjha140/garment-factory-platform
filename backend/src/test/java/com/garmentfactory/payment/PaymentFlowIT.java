package com.garmentfactory.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.payment.api.GatewayOrder;
import com.garmentfactory.payment.api.GatewayPayment;
import com.garmentfactory.payment.api.PaymentGateway;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Payment flow against a real MySQL 8.4.
 * Provider calls and webhook signature verification are handled by a test-local fake.
 * Requires Docker.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.bootstrap.admin-email=admin@example.com",
                "app.bootstrap.admin-password=Adm1nStrongPass",
                "app.bootstrap.admin-name=Root Admin",
                "app.razorpay.key-id=rzp_test_key",
                "app.razorpay.key-secret=test_key_secret",
                "app.razorpay.webhook-secret=whsec_test_secret"
        }
)
class PaymentFlowIT {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String WEBHOOK = "/api/v1/payments/razorpay/webhook";
    private static final String CATALOG = "/api/v1/admin/catalog";
    private static final String PASSWORD = "Str0ngPassw0rd";

    private static final Pattern ID = Pattern.compile("\"id\":\"([^\"]+)\"");
    private static final Pattern ORDER_NUMBER = Pattern.compile("\"orderNumber\":\"([^\"]+)\"");
    private static final Pattern RZP_ORDER = Pattern.compile("\"razorpayOrderId\":\"([^\"]+)\"");

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--log-bin-trust-function-creators=1");

    static class FakeGateway implements PaymentGateway {

        final Map<String, GatewayPayment> payments = new ConcurrentHashMap<>();

        @Override
        public String publicKeyId() {
            return "rzp_test_key";
        }

        @Override
        public GatewayOrder createOrder(long amountPaise, String currency, String receipt) {
            return new GatewayOrder(
                    "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14),
                    amountPaise,
                    currency
            );
        }

        @Override
        public GatewayPayment fetchPayment(String providerPaymentId) {
            GatewayPayment payment = payments.get(providerPaymentId);

            if (payment == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "PAYMENT_GATEWAY_ERROR",
                        "unknown payment"
                );
            }

            return payment;
        }

        @Override
        public boolean verifyWebhookSignature(String rawBody, String signature) {
            return signature != null && signature.equals(sign(WEBHOOK_SECRET, rawBody));
        }
    }

    @TestConfiguration
    static class GatewayConfig {

        @Bean
        @Primary
        FakeGateway fakeGateway() {
            return new FakeGateway();
        }
    }

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CapturingEmailSender emails;

    @Autowired
    private FakeGateway gateway;

    private ApiClient api;
    private String admin;

    private record Placed(
            String token,
            String email,
            String variantId,
            String orderNumber,
            long orderId,
            BigDecimal grandTotal
    ) {
    }

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(environment);

        Response login = api.post(
                "/api/v1/auth/login",
                ApiClient.loginJson("admin@example.com", "Adm1nStrongPass")
        );

        assertThat(login.status()).isEqualTo(200);
        admin = login.accessToken();
    }

    // ---------- setup helpers ----------

    private Response as(String token, String method, String path, String body) throws Exception {
        return api.send(
                method,
                path,
                body,
                Map.of("Authorization", "Bearer " + token)
        );
    }

    private static String firstId(Response response) {
        Matcher matcher = ID.matcher(response.body());

        assertThat(matcher.find())
                .as("id in %s", response.body())
                .isTrue();

        return matcher.group(1);
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String sellableVariant(int stock) throws Exception {
        Response category = as(
                admin,
                "POST",
                CATALOG + "/categories",
                "{\"name\":\"" + unique("Cat") + "\"}"
        );

        Response product = as(
                admin,
                "POST",
                CATALOG + "/products",
                "{\"categoryId\":\"" + firstId(category)
                        + "\",\"brandId\":null,\"name\":\"" + unique("Tee ")
                        + "\",\"description\":\"Soft cotton tee\","
                        + "\"fabric\":\"Cotton\",\"gsm\":180,\"composition\":\"100% cotton\","
                        + "\"hsnCode\":\"6109\",\"gstRate\":5,\"basePrice\":500.00,"
                        + "\"mrp\":700.00,\"customizable\":true}"
        );

        assertThat(product.status())
                .as(product.body())
                .isEqualTo(201);

        String productId = firstId(product);

        Response variant = as(
                admin,
                "POST",
                CATALOG + "/products/" + productId + "/variants",
                "{\"sku\":\"" + unique("SKU-")
                        + "\",\"size\":\"M\",\"colorName\":\"Red\",\"colorHex\":\"#FF0000\","
                        + "\"priceOverride\":null,\"weightG\":180,\"barcode\":null}"
        );

        assertThat(variant.status())
                .as(variant.body())
                .isEqualTo(201);

        String variantId = firstId(variant);

        long id = jdbc.queryForObject(
                "SELECT id FROM product_variants WHERE public_id = ?",
                Long.class,
                variantId
        );

        if (jdbc.update(
                "UPDATE finished_goods_stock SET on_hand = ? WHERE variant_id = ?",
                stock,
                id
        ) == 0) {
            assertThat(
                    as(
                            admin,
                            "POST",
                            "/api/v1/inventory/stock/initialize-missing",
                            null
                    ).status()
            ).isBetween(200, 204);

            jdbc.update(
                    "UPDATE finished_goods_stock SET on_hand = ? WHERE variant_id = ?",
                    stock,
                    id
            );
        }

        assertThat(
                as(
                        admin,
                        "POST",
                        CATALOG + "/products/" + productId + "/status",
                        "{\"status\":\"ACTIVE\"}"
                ).status()
        ).isEqualTo(200);

        return variantId;
    }

    private String registerAndLogin(String email) throws Exception {
        assertThat(
                api.post(
                        "/api/v1/auth/register",
                        ApiClient.registerJson(email, PASSWORD)
                ).status()
        ).isEqualTo(202);

        assertThat(
                api.post(
                        "/api/v1/auth/verify-email",
                        "{\"token\":\"" + emails.latestTokenTo(email) + "\"}"
                ).status()
        ).isIn(200, 204);

        return api.post(
                "/api/v1/auth/login",
                ApiClient.loginJson(email, PASSWORD)
        ).accessToken();
    }

    /**
     * Customer with an address, units of a fresh variant in the cart
     * (stock 10), and a placed order.
     */
    private Placed placedOrder(int units) throws Exception {
        String variantId = sellableVariant(10);
        String email = "pay-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        Response address = as(
                token,
                "POST",
                "/api/v1/customers/me/addresses",
                "{\"label\":\"Home\",\"recipientName\":\"Test User\","
                        + "\"phone\":\"9876543210\",\"line1\":\"12 MG Road\","
                        + "\"line2\":null,\"city\":\"Gurugram\",\"state\":\"Haryana\","
                        + "\"pincode\":\"122001\",\"defaultShipping\":true,"
                        + "\"defaultBilling\":true}"
        );

        assertThat(address.status()).isEqualTo(201);

        assertThat(
                as(
                        token,
                        "POST",
                        "/api/v1/cart/items",
                        "{\"variantId\":\"" + variantId + "\",\"quantity\":" + units + "}"
                ).status()
        ).isBetween(200, 201);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Idempotency-Key", unique("idem-key-"));

        Response placed = api.send(
                "POST",
                "/api/v1/orders",
                "{\"shippingAddressId\":\"" + firstId(address)
                        + "\",\"billingAddressId\":null}",
                headers
        );

        assertThat(placed.status())
                .as(placed.body())
                .isEqualTo(201);

        Matcher number = ORDER_NUMBER.matcher(placed.body());

        assertThat(number.find()).isTrue();

        long orderId = jdbc.queryForObject(
                "SELECT id FROM orders WHERE order_number = ?",
                Long.class,
                number.group(1)
        );

        BigDecimal grand = jdbc.queryForObject(
                "SELECT grand_total FROM orders WHERE id = ?",
                BigDecimal.class,
                orderId
        );

        return new Placed(
                token,
                email,
                variantId,
                number.group(1),
                orderId,
                grand
        );
    }

    private String initiate(Placed order) throws Exception {
        Response response = as(
                order.token(),
                "POST",
                "/api/v1/payments/orders/" + order.orderNumber() + "/initiate",
                null
        );

        assertThat(response.status())
                .as(response.body())
                .isEqualTo(200);

        Matcher matcher = RZP_ORDER.matcher(response.body());

        assertThat(matcher.find()).isTrue();

        return matcher.group(1);
    }

    // ---------- webhook helpers ----------

    private static String event(
            String type,
            String razorpayOrderId,
            String paymentId,
            long amountPaise
    ) {
        return "{\"event\":\"" + type
                + "\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + paymentId
                + "\",\"order_id\":\"" + razorpayOrderId
                + "\",\"amount\":" + amountPaise
                + ",\"currency\":\"INR\",\"status\":\""
                + ("payment.failed".equals(type) ? "failed" : "captured")
                + "\",\"error_description\":\"Card declined\"}}}}";
    }

    private Response webhook(String body, String eventId, String signature) throws Exception {
        return api.send(
                "POST",
                WEBHOOK,
                body,
                Map.of(
                        "X-Razorpay-Signature", signature,
                        "X-Razorpay-Event-Id", eventId
                )
        );
    }

    private Response signedWebhook(String body, String eventId) throws Exception {
        return webhook(body, eventId, sign(WEBHOOK_SECRET, body));
    }

    private static String sign(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(
                    new SecretKeySpec(
                            secret.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"
                    )
            );

            return HexFormat.of().formatHex(
                    mac.doFinal(body.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }

    private static long paise(BigDecimal rupees) {
        return rupees.movePointRight(2).longValueExact();
    }

    private String orderStatus(long id) {
        return jdbc.queryForObject(
                "SELECT order_status FROM orders WHERE id = ?",
                String.class,
                id
        );
    }

    private String paymentStatus(long id) {
        return jdbc.queryForObject(
                "SELECT payment_status FROM orders WHERE id = ?",
                String.class,
                id
        );
    }

    private int onHand(String variantPublicId) {
        return jdbc.queryForObject(
                """
                SELECT on_hand FROM finished_goods_stock
                 WHERE variant_id = (SELECT id FROM product_variants WHERE public_id = ?)
                """,
                Integer.class,
                variantPublicId
        );
    }

    private long historyRows(long orderId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_status_history WHERE order_id = ?",
                Long.class,
                orderId
        );
    }

    private long eventRows(String eventId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_events WHERE provider_event_id = ?",
                Long.class,
                eventId
        );
    }

    // ---------- tests ----------

    @Test
    void initiatingPaymentCreatesOneProviderOrderAndIsRepeatable() throws Exception {
        Placed order = placedOrder(2);

        String first = initiate(order);
        String second = initiate(order);

        assertThat(second).isEqualTo(first);

        assertThat(
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM payments WHERE order_id = ?",
                        Long.class,
                        order.orderId()
                )
        ).isEqualTo(1);

        assertThat(
                jdbc.queryForObject(
                        "SELECT amount FROM payments WHERE order_id = ?",
                        BigDecimal.class,
                        order.orderId()
                )
        ).isEqualByComparingTo(order.grandTotal());
    }

    @Test
    void aSignedCapturedWebhookConfirmsTheOrderAndDeductsStock() throws Exception {
        Placed order = placedOrder(2);
        String rzpOrder = initiate(order);

        assertThat(onHand(order.variantId())).isEqualTo(10);

        Response response = signedWebhook(
                event(
                        "payment.captured",
                        rzpOrder,
                        "pay_test0001",
                        paise(order.grandTotal())
                ),
                unique("evt_")
        );

        assertThat(response.status()).isEqualTo(200);
        assertThat(paymentStatus(order.orderId())).isEqualTo("PAID");
        assertThat(orderStatus(order.orderId())).isEqualTo("CONFIRMED");

        assertThat(
                jdbc.queryForObject(
                        "SELECT amount_paid FROM orders WHERE id = ?",
                        BigDecimal.class,
                        order.orderId()
                )
        ).isEqualByComparingTo(order.grandTotal());

        assertThat(
                jdbc.queryForObject(
                        "SELECT status FROM payments WHERE order_id = ?",
                        String.class,
                        order.orderId()
                )
        ).isEqualTo("CAPTURED");

        assertThat(
                jdbc.queryForObject(
                        "SELECT status FROM stock_reservations WHERE order_id = ?",
                        String.class,
                        order.orderId()
                )
        ).isEqualTo("CONSUMED");

        assertThat(onHand(order.variantId())).isEqualTo(8);
        assertThat(historyRows(order.orderId())).isEqualTo(6);
    }

    @Test
    void aRepeatedWebhookIsAcknowledgedButProcessedOnlyOnce() throws Exception {
        Placed order = placedOrder(2);
        String rzpOrder = initiate(order);
        String eventId = unique("evt_");

        String body = event(
                "payment.captured",
                rzpOrder,
                "pay_test0002",
                paise(order.grandTotal())
        );

        Response first = signedWebhook(body, eventId);
        Response second = signedWebhook(body, eventId);

        assertThat(first.status()).isEqualTo(200);
        assertThat(second.status()).isEqualTo(200);
        assertThat(eventRows(eventId)).isEqualTo(1);
        assertThat(onHand(order.variantId())).isEqualTo(8);
        assertThat(historyRows(order.orderId())).isEqualTo(6);
    }

    @Test
    void theSamePaymentUnderANewEventIdIsStillSettledOnlyOnce() throws Exception {
        Placed order = placedOrder(2);
        String rzpOrder = initiate(order);

        String body = event(
                "payment.captured",
                rzpOrder,
                "pay_test0003",
                paise(order.grandTotal())
        );

        assertThat(signedWebhook(body, unique("evt_")).status()).isEqualTo(200);
        assertThat(signedWebhook(body, unique("evt_")).status()).isEqualTo(200);

        // e.g. order.paid after payment.captured
        assertThat(onHand(order.variantId())).isEqualTo(8);
        assertThat(historyRows(order.orderId())).isEqualTo(6);
    }

    @Test
    void anInvalidSignatureIsRejectedStoresNothingAndCannotBlockTheRealEvent() throws Exception {
        Placed order = placedOrder(2);
        String rzpOrder = initiate(order);
        String eventId = unique("evt_");

        String body = event(
                "payment.captured",
                rzpOrder,
                "pay_test0004",
                paise(order.grandTotal())
        );

        Response forged = webhook(body, eventId, "0".repeat(64));

        assertThat(forged.status()).isEqualTo(401);
        assertThat(paymentStatus(order.orderId())).isEqualTo("PENDING");
        assertThat(eventRows(eventId)).isZero();

        // Same event id, now genuine
        assertThat(signedWebhook(body, eventId).status()).isEqualTo(200);
        assertThat(paymentStatus(order.orderId())).isEqualTo("PAID");
    }

    @Test
    void aCapturedAmountThatDoesNotMatchNeverConfirmsTheOrder() throws Exception {
        Placed order = placedOrder(2);
        String rzpOrder = initiate(order);
        String eventId = unique("evt_");

        Response response = signedWebhook(
                event(
                        "payment.captured",
                        rzpOrder,
                        "pay_test0005",
                        paise(order.grandTotal()) - 1
                ),
                eventId
        );

        assertThat(response.status()).isEqualTo(200);
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentStatus(order.orderId())).isEqualTo("PENDING");
        assertThat(onHand(order.variantId())).isEqualTo(10);

        assertThat(
                jdbc.queryForObject(
                        "SELECT processing_error FROM payment_events WHERE provider_event_id = ?",
                        String.class,
                        eventId
                )
        ).isNotNull();
    }

    @Test
    void onlyTheProvidersOwnStatusCanConfirmAPaymentThroughSync() throws Exception {
        Placed order = placedOrder(2);
        String rzpOrder = initiate(order);

        String path = "/api/v1/payments/orders/" + order.orderNumber() + "/sync";
        String syncBody = "{\"razorpayPaymentId\":\"pay_sync0001\"}";

        gateway.payments.put(
                "pay_sync0001",
                new GatewayPayment(
                        "pay_sync0001",
                        rzpOrder,
                        paise(order.grandTotal()),
                        "INR",
                        "created",
                        null
                )
        );

        Response pending = as(order.token(), "POST", path, syncBody);

        assertThat(pending.status())
                .as(pending.body())
                .isEqualTo(200);

        assertThat(pending.body()).contains("\"paymentStatus\":\"PENDING\"");
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");

        gateway.payments.put(
                "pay_sync0001",
                new GatewayPayment(
                        "pay_sync0001",
                        rzpOrder,
                        paise(order.grandTotal()),
                        "INR",
                        "captured",
                        null
                )
        );

        Response paid = as(order.token(), "POST", path, syncBody);

        assertThat(paid.body())
                .contains("\"paymentStatus\":\"PAID\"")
                .contains("\"orderStatus\":\"CONFIRMED\"");

        assertThat(onHand(order.variantId())).isEqualTo(8);
    }

    @Test
    void aPaymentOfAnotherProviderOrderCannotBeUsedToConfirmThisOrder() throws Exception {
        Placed order = placedOrder(2);

        initiate(order);

        gateway.payments.put(
                "pay_other001",
                new GatewayPayment(
                        "pay_other001",
                        "order_someoneelse",
                        paise(order.grandTotal()),
                        "INR",
                        "captured",
                        null
                )
        );

        Response response = as(
                order.token(),
                "POST",
                "/api/v1/payments/orders/" + order.orderNumber() + "/sync",
                "{\"razorpayPaymentId\":\"pay_other001\"}"
        );

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body()).contains("PAYMENT_ORDER_MISMATCH");
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void aFailedPaymentCanBeRetriedOnTheSameProviderOrderAndThenSucceed() throws Exception {
        Placed order = placedOrder(2);
        String rzpOrder = initiate(order);

        assertThat(
                signedWebhook(
                        event(
                                "payment.failed",
                                rzpOrder,
                                "pay_fail0001",
                                paise(order.grandTotal())
                        ),
                        unique("evt_")
                ).status()
        ).isEqualTo(200);

        assertThat(paymentStatus(order.orderId())).isEqualTo("FAILED");
        assertThat(orderStatus(order.orderId())).isEqualTo("PENDING_PAYMENT");

        assertThat(initiate(order)).isEqualTo(rzpOrder);
        assertThat(paymentStatus(order.orderId())).isEqualTo("PENDING");

        assertThat(
                signedWebhook(
                        event(
                                "payment.captured",
                                rzpOrder,
                                "pay_fail0002",
                                paise(order.grandTotal())
                        ),
                        unique("evt_")
                ).status()
        ).isEqualTo(200);

        assertThat(paymentStatus(order.orderId())).isEqualTo("PAID");
        assertThat(orderStatus(order.orderId())).isEqualTo("CONFIRMED");
    }

    @Test
    void aFailureNeverUndoesAPaidOrder() throws Exception {
        Placed order = placedOrder(2);
        String rzpOrder = initiate(order);

        assertThat(
                signedWebhook(
                        event(
                                "payment.captured",
                                rzpOrder,
                                "pay_late0001",
                                paise(order.grandTotal())
                        ),
                        unique("evt_")
                ).status()
        ).isEqualTo(200);

        assertThat(
                signedWebhook(
                        event(
                                "payment.failed",
                                rzpOrder,
                                "pay_late0002",
                                paise(order.grandTotal())
                        ),
                        unique("evt_")
                ).status()
        ).isEqualTo(200);

        assertThat(paymentStatus(order.orderId())).isEqualTo("PAID");
        assertThat(orderStatus(order.orderId())).isEqualTo("CONFIRMED");
    }

    @Test
    void nobodyCanStartPaymentOnSomeoneElsesOrder() throws Exception {
        Placed order = placedOrder(1);
        String otherToken = registerAndLogin(
                "other-" + UUID.randomUUID() + "@example.com"
        );

        Response response = as(
                otherToken,
                "POST",
                "/api/v1/payments/orders/" + order.orderNumber() + "/initiate",
                null
        );

        assertThat(response.status()).isEqualTo(404);
    }
}