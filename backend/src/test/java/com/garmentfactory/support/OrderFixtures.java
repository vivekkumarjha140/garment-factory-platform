package com.garmentfactory.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.integration.razorpay.WebhookSignature;
import com.garmentfactory.support.ApiClient.Response;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/** Builds the world an order test needs: catalog item with stock, a customer, an address, a placed order. */
public class OrderFixtures {

    public static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String CATALOG = "/api/v1/admin/catalog";
    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final Pattern ID = Pattern.compile("\"id\":\"([^\"]+)\"");
    private static final Pattern ORDER_NUMBER = Pattern.compile("\"orderNumber\":\"([^\"]+)\"");
    private static final Pattern RZP_ORDER = Pattern.compile("\"razorpayOrderId\":\"([^\"]+)\"");

    public record Placed(String token, String email, String variantId, String addressId, String orderNumber,
                         long orderId, BigDecimal grandTotal) {
    }

    private record Setup(String token, String email, String variantId, String addressId) {
    }

    private final ApiClient api;
    private final JdbcTemplate jdbc;
    private final CapturingEmailSender emails;
    private final String admin;

    public OrderFixtures(ApiClient api, JdbcTemplate jdbc, CapturingEmailSender emails, String adminToken) {
        this.api = api;
        this.jdbc = jdbc;
        this.emails = emails;
        this.admin = adminToken;
    }

    public static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public Response as(String token, String method, String path, String body) throws Exception {
        return api.send(method, path, body, Map.of("Authorization", "Bearer " + token));
    }

    private static String firstId(Response response) {
        Matcher matcher = ID.matcher(response.body());
        assertThat(matcher.find()).as("id in %s", response.body()).isTrue();
        return matcher.group(1);
    }

    public String sellableVariant(int stock) throws Exception {
        Response category = as(admin, "POST", CATALOG + "/categories", "{\"name\":\"" + unique("Cat") + "\"}");
        Response product = as(admin, "POST", CATALOG + "/products", "{\"categoryId\":\"" + firstId(category)
                + "\",\"brandId\":null,\"name\":\"" + unique("Tee ") + "\",\"description\":\"Soft cotton tee\","
                + "\"fabric\":\"Cotton\",\"gsm\":180,\"composition\":\"100% cotton\",\"hsnCode\":\"6109\","
                + "\"gstRate\":5,\"basePrice\":500.00,\"mrp\":700.00,\"customizable\":true}");
        assertThat(product.status()).as(product.body()).isEqualTo(201);
        String productId = firstId(product);
        Response variant = as(admin, "POST", CATALOG + "/products/" + productId + "/variants",
                "{\"sku\":\"" + unique("SKU-") + "\",\"size\":\"M\",\"colorName\":\"Red\",\"colorHex\":\"#FF0000\","
                        + "\"priceOverride\":null,\"weightG\":180,\"barcode\":null}");
        assertThat(variant.status()).as(variant.body()).isEqualTo(201);
        String variantId = firstId(variant);
        long id = jdbc.queryForObject("SELECT id FROM product_variants WHERE public_id = ?", Long.class, variantId);
        if (jdbc.update("UPDATE finished_goods_stock SET on_hand = ? WHERE variant_id = ?", stock, id) == 0) {
            assertThat(as(admin, "POST", "/api/v1/inventory/stock/initialize-missing", null).status()).isBetween(200, 204);
            jdbc.update("UPDATE finished_goods_stock SET on_hand = ? WHERE variant_id = ?", stock, id);
        }
        assertThat(as(admin, "POST", CATALOG + "/products/" + productId + "/status", "{\"status\":\"ACTIVE\"}").status()).isEqualTo(200);
        return variantId;
    }

    public String registerAndLogin(String email) throws Exception {
        assertThat(api.post("/api/v1/auth/register", ApiClient.registerJson(email, PASSWORD)).status()).isEqualTo(202);
        assertThat(api.post("/api/v1/auth/verify-email", "{\"token\":\"" + emails.latestTokenTo(email) + "\"}").status()).isIn(200, 204);
        return api.post("/api/v1/auth/login", ApiClient.loginJson(email, PASSWORD)).accessToken();
    }

    /** A percentage or fixed coupon that is valid right now. */
    public String coupon(String discountType, String value) {
        String code = unique("T").toUpperCase();
        jdbc.update("""
                INSERT INTO coupons (code, description, discount_type, discount_value, max_discount, min_order_amount,
                                     valid_from, valid_until, active, version, created_at, updated_at)
                VALUES (?, 'test coupon', ?, ?, NULL, 0, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, NULL, 1, 0,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, code, discountType, new BigDecimal(value));
        return code;
    }

    /** A customer in Haryana with `units` of a fresh variant (stock 10) in the cart, optionally with a coupon applied. */
    private Setup setup(int units, String couponCode) throws Exception {
        String variantId = sellableVariant(10);
        String email = "fx-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        Response address = as(token, "POST", "/api/v1/customers/me/addresses",
                "{\"label\":\"Home\",\"recipientName\":\"Test User\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\","
                        + "\"line2\":null,\"city\":\"Gurugram\",\"state\":\"Haryana\",\"pincode\":\"122001\","
                        + "\"defaultShipping\":true,\"defaultBilling\":true}");
        assertThat(address.status()).isEqualTo(201);
        assertThat(as(token, "POST", "/api/v1/cart/items", "{\"variantId\":\"" + variantId + "\",\"quantity\":" + units + "}").status()).isBetween(200, 201);
        if (couponCode != null) {
            Response applied = as(token, "POST", "/api/v1/cart/coupon", "{\"code\":\"" + couponCode + "\"}");
            assertThat(applied.status()).as(applied.body()).isEqualTo(200);
        }
        return new Setup(token, email, variantId, firstId(address));
    }

    private Response place(Setup s, String paymentMethod) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + s.token());
        headers.put("Idempotency-Key", unique("idem-key-"));
        return api.send("POST", "/api/v1/orders", "{\"shippingAddressId\":\"" + s.addressId()
                + "\",\"billingAddressId\":null,\"paymentMethod\":\"" + paymentMethod + "\"}", headers);
    }

    private Placed toPlaced(Setup s, Response placed) {
        assertThat(placed.status()).as(placed.body()).isEqualTo(201);
        Matcher number = ORDER_NUMBER.matcher(placed.body());
        assertThat(number.find()).isTrue();
        long orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_number = ?", Long.class, number.group(1));
        BigDecimal grand = jdbc.queryForObject("SELECT grand_total FROM orders WHERE id = ?", BigDecimal.class, orderId);
        return new Placed(s.token(), s.email(), s.variantId(), s.addressId(), number.group(1), orderId, grand);
    }

    public Placed placedOrder(int units) throws Exception {
        return placedOrder(units, "ONLINE", null);
    }

    public Placed placedOrder(int units, String paymentMethod, String couponCode) throws Exception {
        Setup s = setup(units, couponCode);
        return toPlaced(s, place(s, paymentMethod));
    }

    /** For tests that expect the order to be refused: returns the raw response. */
    public Response tryPlace(int units, String paymentMethod) throws Exception {
        return place(setup(units, null), paymentMethod);
    }

    /** Another order for the same customer (same item and address). Returns the raw response. */
    public Response placeMore(Placed customer, int units, String paymentMethod) throws Exception {
        assertThat(as(customer.token(), "POST", "/api/v1/cart/items",
                "{\"variantId\":\"" + customer.variantId() + "\",\"quantity\":" + units + "}").status()).isBetween(200, 201);
        return place(new Setup(customer.token(), customer.email(), customer.variantId(), customer.addressId()), paymentMethod);
    }

    public Response cancel(Placed order) throws Exception {
        return as(order.token(), "POST", "/api/v1/orders/" + order.orderNumber() + "/cancel", "{\"reason\":\"changed my mind\"}");
    }

    /** Starts payment and returns the (fake) Razorpay order id. */
    public String initiate(Placed order) throws Exception {
        Response response = as(order.token(), "POST", "/api/v1/payments/orders/" + order.orderNumber() + "/initiate", null);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        Matcher matcher = RZP_ORDER.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    /** Delivers a correctly signed payment.captured webhook for the full amount. */
    public Response captureByWebhook(Placed order, String razorpayOrderId, String eventId) throws Exception {
        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + unique("pay_")
                + "\",\"order_id\":\"" + razorpayOrderId + "\",\"amount\":" + order.grandTotal().movePointRight(2).longValueExact()
                + ",\"currency\":\"INR\",\"status\":\"captured\"}}}}";
        return api.send("POST", "/api/v1/payments/razorpay/webhook", body,
                Map.of("X-Razorpay-Signature", WebhookSignature.sign(WEBHOOK_SECRET, body), "X-Razorpay-Event-Id", eventId));
    }

    /** Places an online order, starts payment and confirms it by webhook. */
    public Placed paidOrder(int units) throws Exception {
        Placed order = placedOrder(units);
        assertThat(captureByWebhook(order, initiate(order), unique("evt_")).status()).isEqualTo(200);
        return order;
    }
}