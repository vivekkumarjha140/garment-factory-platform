package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
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

/** Order creation end to end against a real MySQL 8.4. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin"})
class OrderCreationApiIT {

    private static final String CATALOG = "/api/v1/admin/catalog";
    private static final String CART = "/api/v1/cart";
    private static final String ORDERS = "/api/v1/orders";
    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final Pattern ID = Pattern.compile("\"id\":\"([^\"]+)\"");
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
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(environment);
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson("admin@example.com", "Adm1nStrongPass"));
        assertThat(login.status()).as("bootstrap admin login: %s", login.body()).isEqualTo(200);
        admin = login.accessToken();
    }

    // ---------- helpers ----------

    private record Item(String productId, String variantId) {
    }

    private record Customer(String token, String email) {
    }

    private Response as(String token, String method, String path, String body) throws Exception {
        return api.send(method, path, body, Map.of("Authorization", "Bearer " + token));
    }

    private static String firstId(Response response) {
        Matcher matcher = ID.matcher(response.body());
        assertThat(matcher.find()).as("id in %s", response.body()).isTrue();
        return matcher.group(1);
    }

    private static String orderNumber(Response response) {
        Matcher matcher = ORDER_NUMBER.matcher(response.body());
        assertThat(matcher.find()).as("orderNumber in %s", response.body()).isTrue();
        return matcher.group(1);
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Item sellable(String base, String gst, int stock) throws Exception {
        Response category = as(admin, "POST", CATALOG + "/categories", "{\"name\":\"" + unique("Cat") + "\"}");
        assertThat(category.status()).as("category: %s", category.body()).isEqualTo(201);
        String mrp = new BigDecimal(base).add(new BigDecimal("200.00")).toPlainString();
        Response product = as(admin, "POST", CATALOG + "/products", "{\"categoryId\":\"" + firstId(category)
                + "\",\"brandId\":null,\"name\":\"" + unique("Tee ") + "\",\"description\":\"Soft cotton tee\","
                + "\"fabric\":\"Cotton\",\"gsm\":180,\"composition\":\"100% cotton\",\"hsnCode\":\"6109\","
                + "\"gstRate\":" + gst + ",\"basePrice\":" + base + ",\"mrp\":" + mrp + ",\"customizable\":true}");
        assertThat(product.status()).as("product: %s", product.body()).isEqualTo(201);
        String productId = firstId(product);
        Response variant = as(admin, "POST", CATALOG + "/products/" + productId + "/variants",
                "{\"sku\":\"" + unique("SKU-") + "\",\"size\":\"M\",\"colorName\":\"Red\",\"colorHex\":\"#FF0000\","
                        + "\"priceOverride\":null,\"weightG\":180,\"barcode\":null}");
        assertThat(variant.status()).as("variant: %s", variant.body()).isEqualTo(201);
        String variantId = firstId(variant);
        setStock(variantId, stock);
        Response publish = as(admin, "POST", CATALOG + "/products/" + productId + "/status", "{\"status\":\"ACTIVE\"}");
        assertThat(publish.status()).as("publish: %s", publish.body()).isEqualTo(200);
        return new Item(productId, variantId);
    }

    private void setStock(String variantPublicId, int onHand) throws Exception {
        List<Long> ids = jdbc.queryForList("SELECT id FROM product_variants WHERE public_id = ?", Long.class, variantPublicId);
        assertThat(ids).hasSize(1);
        int updated = jdbc.update("UPDATE finished_goods_stock SET on_hand = ? WHERE variant_id = ?", onHand, ids.get(0));
        if (updated == 0) {
            Response init = as(admin, "POST", "/api/v1/inventory/stock/initialize-missing", null);
            assertThat(init.status()).as("initialize-missing: %s", init.body()).isBetween(200, 204);
            updated = jdbc.update("UPDATE finished_goods_stock SET on_hand = ? WHERE variant_id = ?", onHand, ids.get(0));
        }
        assertThat(updated).isEqualTo(1);
    }

    private String coupon(String type, String value) {
        String code = unique("T").toUpperCase();
        jdbc.update("""
                INSERT INTO coupons (code, description, discount_type, discount_value, max_discount, min_order_amount,
                                     valid_from, valid_until, active, version, created_at, updated_at)
                VALUES (?, 'test coupon', ?, ?, NULL, 0, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, NULL, 1, 0,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, code, type, new BigDecimal(value));
        return code;
    }

    private Customer newCustomer() throws Exception {
        String email = "order-" + UUID.randomUUID() + "@example.com";
        assertThat(api.post("/api/v1/auth/register", ApiClient.registerJson(email, PASSWORD)).status()).isEqualTo(202);
        Response verification = api.post("/api/v1/auth/verify-email", "{\"token\":\"" + emails.latestTokenTo(email) + "\"}");
        assertThat(verification.status()).isIn(200, 204);
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson(email, PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        return new Customer(login.accessToken(), email);
    }

    private String addAddress(String token, String pincode) throws Exception {
        Response created = as(token, "POST", "/api/v1/customers/me/addresses",
                "{\"label\":\"Home\",\"recipientName\":\"Test User\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\","
                        + "\"line2\":null,\"city\":\"Gurugram\",\"state\":\"Haryana\",\"pincode\":\"" + pincode
                        + "\",\"defaultShipping\":true,\"defaultBilling\":true}");
        assertThat(created.status()).as("address: %s", created.body()).isEqualTo(201);
        return firstId(created);
    }

    private void addToCart(String token, String variantId, int quantity) throws Exception {
        Response added = as(token, "POST", CART + "/items", "{\"variantId\":\"" + variantId + "\",\"quantity\":" + quantity + "}");
        assertThat(added.status()).as("add to cart: %s", added.body()).isBetween(200, 201);
    }

    private Response place(String token, String key, String shippingId, String billingId) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        if (key != null) {
            headers.put("Idempotency-Key", key);
        }
        String body = "{\"shippingAddressId\":\"" + shippingId + "\",\"billingAddressId\":"
                + (billingId == null ? "null" : "\"" + billingId + "\"") + "}";
        return api.send("POST", ORDERS, body, headers);
    }

    private long ordersOf(Customer customer) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders o JOIN users u ON u.id = o.user_id WHERE u.email = ?",
                Long.class, customer.email());
    }

    private long cartLinesOf(Customer customer) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM cart_items ci JOIN carts c ON c.id = ci.cart_id
                JOIN users u ON u.id = c.user_id WHERE u.email = ?
                """, Long.class, customer.email());
    }

    private long orderId(String orderNumber) {
        return jdbc.queryForObject("SELECT id FROM orders WHERE order_number = ?", Long.class, orderNumber);
    }

    private long activeReservedFor(long orderId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM stock_reservations WHERE order_id = ? AND status = 'ACTIVE'",
                Long.class, orderId);
    }

    // ---------- tests ----------

    @Test
    void placingAnOrderFreezesPricesReservesStockAndEmptiesTheCart() throws Exception {
        Item item = sellable("500.00", "5", 10);
        Customer customer = newCustomer();
        String address = addAddress(customer.token(), "122001");
        addToCart(customer.token(), item.variantId(), 2);

        Response placed = place(customer.token(), unique("idem-key-"), address, null);

        assertThat(placed.status()).as(placed.body()).isEqualTo(201);
        assertThat(placed.body()).contains("\"orderStatus\":\"PENDING_PAYMENT\"").contains("\"paymentStatus\":\"PENDING\"")
                .contains("\"productionStatus\":\"NOT_REQUIRED\"").contains("\"shipmentStatus\":\"NOT_SHIPPED\"");
        String number = orderNumber(placed);
        assertThat(number).matches("GF-\\d{4}-\\d{6}");
        long id = orderId(number);

        assertThat(jdbc.queryForObject("SELECT subtotal FROM orders WHERE id = ?", BigDecimal.class, id)).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("SELECT tax_total FROM orders WHERE id = ?", BigDecimal.class, id)).isEqualByComparingTo("50.00");
        assertThat(jdbc.queryForObject("SELECT unit_price FROM order_items WHERE order_id = ?", BigDecimal.class, id)).isEqualByComparingTo("500.00");
        assertThat(jdbc.queryForObject("SELECT line_total FROM order_items WHERE order_id = ?", BigDecimal.class, id)).isEqualByComparingTo("1050.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = ?", Long.class, id)).isEqualTo(4);
        assertThat(activeReservedFor(id)).isEqualTo(2);
        assertThat(cartLinesOf(customer)).isZero();
    }

    @Test
    void orderLinesAndHistoryCanNeverBeEditedOrDeleted() throws Exception {
        Item item = sellable("500.00", "5", 10);
        Customer customer = newCustomer();
        String address = addAddress(customer.token(), "122001");
        addToCart(customer.token(), item.variantId(), 1);
        long id = orderId(orderNumber(place(customer.token(), unique("idem-key-"), address, null)));

        assertThatThrownBy(() -> jdbc.update("UPDATE order_items SET unit_price = 1.00 WHERE order_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM order_items WHERE order_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE order_status_history SET to_status = 'X' WHERE order_id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void theSameKeyReplaysTheSameOrderWithoutReservingTwice() throws Exception {
        Item item = sellable("500.00", "5", 10);
        Customer customer = newCustomer();
        String address = addAddress(customer.token(), "122001");
        addToCart(customer.token(), item.variantId(), 2);
        String key = unique("idem-key-");

        Response first = place(customer.token(), key, address, null);
        Response second = place(customer.token(), key, address, null);

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(orderNumber(second)).isEqualTo(orderNumber(first));
        assertThat(ordersOf(customer)).isEqualTo(1);
        assertThat(activeReservedFor(orderId(orderNumber(first)))).isEqualTo(2);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() throws Exception {
        Item item = sellable("500.00", "5", 10);
        Customer customer = newCustomer();
        String first = addAddress(customer.token(), "122001");
        String second = addAddress(customer.token(), "122001");
        addToCart(customer.token(), item.variantId(), 1);
        String key = unique("idem-key-");

        assertThat(place(customer.token(), key, first, null).status()).isEqualTo(201);
        Response reused = place(customer.token(), key, second, null);

        assertThat(reused.status()).isEqualTo(422);
        assertThat(reused.body()).contains("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void parallelRequestsWithOneKeyCreateExactlyOneOrder() throws Exception {
        Item item = sellable("500.00", "5", 10);
        Customer customer = newCustomer();
        String address = addAddress(customer.token(), "122001");
        addToCart(customer.token(), item.variantId(), 2);
        String key = unique("idem-key-");

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                results.add(pool.submit(() -> place(customer.token(), key, address, null).status()));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnly(200, 201);
            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(ordersOf(customer)).isEqualTo(1);
        long reserved = jdbc.queryForObject("""
                SELECT COALESCE(SUM(r.quantity), 0) FROM stock_reservations r JOIN orders o ON o.id = r.order_id
                JOIN users u ON u.id = o.user_id WHERE u.email = ? AND r.status = 'ACTIVE'
                """, Long.class, customer.email());
        assertThat(reserved).isEqualTo(2);
    }

    @Test
    void aCouponDiscountsTheOrderAndIsRedeemedOnce() throws Exception {
        Item item = sellable("500.00", "5", 10);
        Customer customer = newCustomer();
        String address = addAddress(customer.token(), "122001");
        addToCart(customer.token(), item.variantId(), 2);
        String code = coupon("PERCENTAGE", "10");
        Response applied = as(customer.token(), "POST", CART + "/coupon", "{\"code\":\"" + code + "\"}");
        assertThat(applied.status()).as(applied.body()).isEqualTo(200);

        Response placed = place(customer.token(), unique("idem-key-"), address, null);

        assertThat(placed.status()).as(placed.body()).isEqualTo(201);
        long id = orderId(orderNumber(placed));
        assertThat(jdbc.queryForObject("SELECT coupon_code FROM orders WHERE id = ?", String.class, id)).isEqualTo(code);
        assertThat(jdbc.queryForObject("SELECT discount_total FROM orders WHERE id = ?", BigDecimal.class, id)).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT tax_total FROM orders WHERE id = ?", BigDecimal.class, id)).isEqualByComparingTo("45.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM coupon_redemptions WHERE order_id = ? AND status = 'ACTIVE'", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT times_used FROM coupons WHERE code = ?", Integer.class, code)).isEqualTo(1);
    }

    @Test
    void anOrderIsRefusedWhenStockHasDroppedSinceTheItemWasAdded() throws Exception {
        Item item = sellable("500.00", "5", 2);
        Customer customer = newCustomer();
        String address = addAddress(customer.token(), "122001");
        addToCart(customer.token(), item.variantId(), 2);
        setStock(item.variantId(), 1);

        Response placed = place(customer.token(), unique("idem-key-"), address, null);

        assertThat(placed.status()).isEqualTo(422);
        assertThat(placed.body()).contains("CHECKOUT_CART_NOT_READY");
        assertThat(ordersOf(customer)).isZero();
        assertThat(cartLinesOf(customer)).isEqualTo(1);
    }

    @Test
    void anEmptyCartCannotBeOrdered() throws Exception {
        Customer customer = newCustomer();
        String address = addAddress(customer.token(), "122001");

        Response placed = place(customer.token(), unique("idem-key-"), address, null);

        assertThat(placed.status()).isEqualTo(422);
        assertThat(placed.body()).contains("CART_EMPTY");
    }

    @Test
    void anOrderIsVisibleOnlyToItsOwner() throws Exception {
        Item item = sellable("500.00", "5", 10);
        Customer owner = newCustomer();
        Customer other = newCustomer();
        String address = addAddress(owner.token(), "122001");
        addToCart(owner.token(), item.variantId(), 1);
        String number = orderNumber(place(owner.token(), unique("idem-key-"), address, null));

        assertThat(as(owner.token(), "GET", ORDERS + "/" + number, null).status()).isEqualTo(200);
        assertThat(as(other.token(), "GET", ORDERS + "/" + number, null).status()).isEqualTo(404);
    }

    @Test
    void theIdempotencyKeyHeaderIsRequired() throws Exception {
        Customer customer = newCustomer();
        String address = addAddress(customer.token(), "122001");

        Response placed = place(customer.token(), null, address, null);

        assertThat(placed.status()).isEqualTo(400);
        assertThat(placed.body()).contains("IDEMPOTENCY_KEY_REQUIRED");
    }
}