package com.garmentfactory.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import java.math.BigDecimal;
import java.util.ArrayList;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** The shopping cart end to end against a real MySQL 8.4. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin"})
class CartApiIT {

    private static final String CATALOG = "/api/v1/admin/catalog";
    private static final String CART = "/api/v1/cart";
    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final Pattern ID = Pattern.compile("\"id\":\"([^\"]+)\"");
    private static final Pattern ITEM_ID = Pattern.compile("\"itemId\":(\\d+)");

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

    // ---------- generic helpers ----------

    private Response as(String token, String method, String path, String body) throws Exception {
        return api.send(method, path, body, Map.of("Authorization", "Bearer " + token));
    }

    private static String firstId(Response response) {
        Matcher matcher = ID.matcher(response.body());
        assertThat(matcher.find()).as("id in %s", response.body()).isTrue();
        return matcher.group(1);
    }

    private static long itemId(Response cart) {
        Matcher matcher = ITEM_ID.matcher(cart.body());
        assertThat(matcher.find()).as("itemId in %s", cart.body()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static int count(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    // ---------- catalog / stock helpers (admin side) ----------

    private record Item(String productId, String variantId) {
    }

    private String createCategory() throws Exception {
        Response created = as(admin, "POST", CATALOG + "/categories", "{\"name\":\"" + unique("Cat") + "\"}");
        assertThat(created.status()).as("create category: %s", created.body()).isEqualTo(201);
        return firstId(created);
    }

    private static String productJson(String categoryId, String name, String gst, String base, String mrp) {
        return "{\"categoryId\":\"" + categoryId + "\",\"brandId\":null,\"name\":\"" + name
                + "\",\"description\":\"Soft cotton tee\",\"fabric\":\"Cotton\",\"gsm\":180,\"composition\":\"100% cotton\","
                + "\"hsnCode\":\"6109\",\"gstRate\":" + gst + ",\"basePrice\":" + base + ",\"mrp\":" + mrp + ",\"customizable\":true}";
    }

    private static String variantJson(String sku, String size, String color) {
        return "{\"sku\":\"" + sku + "\",\"size\":\"" + size + "\",\"colorName\":\"" + color
                + "\",\"colorHex\":\"#FF0000\",\"priceOverride\":null,\"weightG\":180,\"barcode\":null}";
    }

    /** Adds a variant (180 g) and returns its public id, which is also what the cart API expects. */
    private String addVariant(String productId, String size, String color) throws Exception {
        Response variant = as(admin, "POST", CATALOG + "/products/" + productId + "/variants",
                variantJson(unique("SKU-"), size, color));
        assertThat(variant.status()).as("add variant: %s", variant.body()).isEqualTo(201);
        String id = firstId(variant);
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE public_id = ?", Integer.class, id);
        assertThat(found).as("the admin variant id should be the variant's public id: %s", variant.body()).isEqualTo(1);
        return id;
    }

    private void setProductStatus(String productId, String status) throws Exception {
        Response response = as(admin, "POST", CATALOG + "/products/" + productId + "/status", "{\"status\":\"" + status + "\"}");
        assertThat(response.status()).as("product -> %s: %s", status, response.body()).isEqualTo(200);
    }

    /** Writes the on-hand quantity straight into the stock table (creating the stock row first if needed). */
    private void setStock(String variantPublicId, int onHand) throws Exception {
        List<Long> ids = jdbc.queryForList("SELECT id FROM product_variants WHERE public_id = ?", Long.class, variantPublicId);
        assertThat(ids).as("variant %s", variantPublicId).hasSize(1);
        long variantId = ids.get(0);

        int updated = jdbc.update("UPDATE finished_goods_stock SET on_hand = ? WHERE variant_id = ?", onHand, variantId);
        if (updated == 0) {
            Response init = as(admin, "POST", "/api/v1/inventory/stock/initialize-missing", null);
            assertThat(init.status()).as("initialize-missing: %s", init.body()).isBetween(200, 204);
            updated = jdbc.update("UPDATE finished_goods_stock SET on_hand = ? WHERE variant_id = ?", onHand, variantId);
        }
        assertThat(updated).as("stock row of variant %s", variantPublicId).isEqualTo(1);
    }

    /** A product with one size-M variant (180 g), stock set, optionally published (ACTIVE). */
    private Item sellable(String base, String gst, int stock, boolean publish) throws Exception {
        String mrp = new BigDecimal(base).add(new BigDecimal("200.00")).toPlainString();
        Response product = as(admin, "POST", CATALOG + "/products",
                productJson(createCategory(), unique("Tee "), gst, base, mrp));
        assertThat(product.status()).as("create product: %s", product.body()).isEqualTo(201);
        String productId = firstId(product);
        String variantId = addVariant(productId, "M", "Red");
        setStock(variantId, stock);
        if (publish) {
            setProductStatus(productId, "ACTIVE");
        }
        return new Item(productId, variantId);
    }

    /** A percentage / fixed coupon valid since yesterday, never expiring. Returns its (upper-case) code. */
    private String coupon(String type, String value, String minOrder) {
        String code = unique("T").toUpperCase();
        jdbc.update("""
                INSERT INTO coupons (code, description, discount_type, discount_value, max_discount, min_order_amount,
                                     valid_from, valid_until, active, version, created_at, updated_at)
                VALUES (?, 'test coupon', ?, ?, NULL, ?, UTC_TIMESTAMP(6) - INTERVAL 1 DAY, NULL, 1, 0,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, code, type, new BigDecimal(value), new BigDecimal(minOrder));
        return code;
    }

    // ---------- customer-side helpers ----------

    private String newCustomer() throws Exception {
        String email = "cart-" + UUID.randomUUID() + "@example.com";
        Response registration = api.post("/api/v1/auth/register", ApiClient.registerJson(email, PASSWORD));
        assertThat(registration.status()).as("register: %s", registration.body()).isEqualTo(202);
        Response verification = api.post("/api/v1/auth/verify-email", "{\"token\":\"" + emails.latestTokenTo(email) + "\"}");
        assertThat(verification.status()).as("verify: %s", verification.body()).isIn(200, 204);
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson(email, PASSWORD));
        assertThat(login.status()).as("login: %s", login.body()).isEqualTo(200);
        return login.accessToken();
    }

    /** Saves an address (the customer's first one becomes the default shipping address) and returns its id. */
    private String addAddress(String token, String pincode) throws Exception {
        Response created = as(token, "POST", "/api/v1/customers/me/addresses",
                "{\"label\":\"Home\",\"recipientName\":\"Test User\",\"phone\":\"9876543210\",\"line1\":\"12 MG Road\","
                        + "\"line2\":null,\"city\":\"Gurugram\",\"state\":\"Haryana\",\"pincode\":\"" + pincode
                        + "\",\"defaultShipping\":true,\"defaultBilling\":true}");
        assertThat(created.status()).as("create address: %s", created.body()).isEqualTo(201);
        return firstId(created);
    }

    private static String addBody(String variantId, int quantity) {
        return "{\"variantId\":\"" + variantId + "\",\"quantity\":" + quantity + "}";
    }

    private Response add(String token, String variantId, int quantity) throws Exception {
        return as(token, "POST", CART + "/items", addBody(variantId, quantity));
    }

    // ---------- basics ----------

    @Test
    void aCartNeedsASignedInCustomer() throws Exception {
        assertThat(api.get(CART, null).status()).isEqualTo(401);
    }

    @Test
    void aNewCustomerStartsWithAnEmptyCart() throws Exception {
        Response cart = as(newCustomer(), "GET", CART, null);

        assertThat(cart.status()).isEqualTo(200);
        assertThat(cart.body()).contains("\"lines\":[]").contains("\"checkoutReady\":false")
                .contains("\"total\":0.00").contains("\"shippingCalculated\":false");
    }

    @Test
    void addingAnItemPricesItLiveWithGst() throws Exception {
        Item item = sellable("500.00", "5.00", 50, true);
        String customer = newCustomer();

        Response cart = add(customer, item.variantId(), 2);

        assertThat(cart.status()).as("add: %s", cart.body()).isEqualTo(200);
        assertThat(cart.body()).contains("\"subtotal\":1000.00").contains("\"gstAmount\":50.00")
                .contains("\"total\":1050.00").contains("\"checkoutReady\":true")
                .contains("\"shippingCalculated\":false");
    }

    @Test
    void aPriceChangeShowsUpInTheCartImmediately() throws Exception {
        Item item = sellable("500.00", "5.00", 50, true);
        String customer = newCustomer();
        assertThat(add(customer, item.variantId(), 1).body()).contains("\"unitPrice\":500.00");

        jdbc.update("UPDATE products SET base_price = 600.00 WHERE public_id = ?", item.productId());

        assertThat(as(customer, "GET", CART, null).body()).contains("\"unitPrice\":600.00");
    }

    @Test
    void addingTheSameVariantAgainMergesIntoOneLine() throws Exception {
        Item item = sellable("500.00", "5.00", 50, true);
        String customer = newCustomer();
        add(customer, item.variantId(), 2);

        Response cart = add(customer, item.variantId(), 3);

        assertThat(count(cart.body(), "\"itemId\"")).isEqualTo(1);
        assertThat(cart.body()).contains("\"quantity\":5");
    }

    // ---------- stock and availability ----------

    @Test
    void youCannotAddMoreThanIsInStock() throws Exception {
        Item item = sellable("500.00", "5.00", 3, true);
        String customer = newCustomer();

        Response tooMany = add(customer, item.variantId(), 4);

        assertThat(tooMany.status()).isEqualTo(409);
        assertThat(tooMany.body()).contains("CART_INSUFFICIENT_STOCK");
        assertThat(as(customer, "GET", CART, null).body()).contains("\"lines\":[]");
    }

    @Test
    void unknownVariantsCannotBeAdded() throws Exception {
        Response response = add(newCustomer(), UUID.randomUUID().toString(), 1);

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body()).contains("CART_VARIANT_UNAVAILABLE");
    }

    @Test
    void aVariantOfAnUnpublishedProductCannotBeAdded() throws Exception {
        Item draft = sellable("500.00", "5.00", 50, false);

        Response response = add(newCustomer(), draft.variantId(), 1);

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body()).contains("CART_VARIANT_UNAVAILABLE");
    }

    @Test
    void stockThatRunsOutAfterAddingIsFlaggedOnTheLine() throws Exception {
        Item item = sellable("500.00", "5.00", 5, true);
        String customer = newCustomer();
        add(customer, item.variantId(), 2);

        setStock(item.variantId(), 0);
        Response cart = as(customer, "GET", CART, null);

        assertThat(cart.body()).contains("\"issue\":\"OUT_OF_STOCK\"").contains("\"availableStock\":0")
                .contains("\"checkoutReady\":false");
    }

    @Test
    void aProductArchivedAfterAddingBecomesUnavailable() throws Exception {
        Item item = sellable("500.00", "5.00", 50, true);
        String customer = newCustomer();
        add(customer, item.variantId(), 2);

        setProductStatus(item.productId(), "ARCHIVED");
        Response cart = as(customer, "GET", CART, null);

        assertThat(cart.body()).contains("\"issue\":\"UNAVAILABLE\"").contains("\"subtotal\":0.00")
                .contains("\"checkoutReady\":false");
    }

    // ---------- changing the cart ----------

    @Test
    void quantityCanBeChangedWithinStockAndTheLineRemoved() throws Exception {
        Item item = sellable("500.00", "5.00", 5, true);
        String customer = newCustomer();
        long line = itemId(add(customer, item.variantId(), 1));

        Response changed = as(customer, "PATCH", CART + "/items/" + line, "{\"quantity\":4}");
        assertThat(changed.status()).isEqualTo(200);
        assertThat(changed.body()).contains("\"quantity\":4").contains("\"subtotal\":2000.00");

        Response tooMany = as(customer, "PATCH", CART + "/items/" + line, "{\"quantity\":6}");
        assertThat(tooMany.status()).isEqualTo(409);
        assertThat(tooMany.body()).contains("CART_INSUFFICIENT_STOCK");

        assertThat(as(customer, "PATCH", CART + "/items/" + line, "{\"quantity\":0}").status()).isEqualTo(400);

        Response removed = as(customer, "DELETE", CART + "/items/" + line, null);
        assertThat(removed.status()).isEqualTo(200);
        assertThat(removed.body()).contains("\"lines\":[]");

        Response again = as(customer, "DELETE", CART + "/items/" + line, null);
        assertThat(again.status()).isEqualTo(404);
        assertThat(again.body()).contains("CART_ITEM_NOT_FOUND");
    }

    @Test
    void theWholeCartCanBeCleared() throws Exception {
        Item item = sellable("500.00", "5.00", 50, true);
        String customer = newCustomer();
        add(customer, item.variantId(), 2);

        Response cleared = as(customer, "DELETE", CART, null);

        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.body()).contains("\"lines\":[]").contains("\"total\":0.00");
    }

    @Test
    void customersNeverSeeEachOthersCarts() throws Exception {
        Item item = sellable("500.00", "5.00", 50, true);
        String first = newCustomer();
        String second = newCustomer();
        add(first, item.variantId(), 2);

        assertThat(as(second, "GET", CART, null).body()).contains("\"lines\":[]");
        assertThat(as(first, "GET", CART, null).body()).contains("\"quantity\":2");
    }

    // ---------- quantity price tiers ----------

    @Test
    void quantityTiersUseTheCombinedQuantityOfAllVariantsOfTheProduct() throws Exception {
        Item item = sellable("500.00", "5.00", 50, false);
        String large = addVariant(item.productId(), "L", "Red");
        setStock(large, 50);
        Response tiers = as(admin, "PUT", CATALOG + "/products/" + item.productId() + "/price-tiers",
                "{\"tiers\":[{\"minQty\":10,\"maxQty\":49,\"unitPrice\":450.00},{\"minQty\":50,\"maxQty\":null,\"unitPrice\":400.00}]}");
        assertThat(tiers.status()).as("tiers: %s", tiers.body()).isEqualTo(200);
        setProductStatus(item.productId(), "ACTIVE");
        String customer = newCustomer();

        // 6 pieces are below the first tier
        Response belowTier = add(customer, item.variantId(), 6);
        assertThat(belowTier.body()).contains("\"unitPrice\":500.00");

        // 6 + 6 = 12 pieces of the same product reach the 10-49 tier for BOTH lines
        Response atTier = add(customer, large, 6);
        assertThat(count(atTier.body(), "\"unitPrice\":450.00")).isEqualTo(2);
        assertThat(atTier.body()).contains("\"subtotal\":5400.00").contains("\"gstAmount\":270.00")
                .contains("\"total\":5670.00");
    }

    // ---------- coupons ----------

    @Test
    void aCouponDiscountsTheGoodsAndGstIsChargedOnTheDiscountedValue() throws Exception {
        Item item = sellable("1000.00", "5.00", 50, true);
        String customer = newCustomer();
        add(customer, item.variantId(), 2);
        String code = coupon("PERCENTAGE", "10", "0");

        Response applied = as(customer, "POST", CART + "/coupon", "{\"code\":\"" + code.toLowerCase() + "\"}");

        assertThat(applied.status()).as("apply: %s", applied.body()).isEqualTo(200);
        assertThat(applied.body()).contains("\"code\":\"" + code + "\"").contains("\"discount\":200.00")
                .contains("\"taxableAmount\":1800.00").contains("\"gstAmount\":90.00").contains("\"total\":1890.00");
        assertThat(as(customer, "GET", CART, null).body()).contains("\"total\":1890.00");

        Response removed = as(customer, "DELETE", CART + "/coupon", null);
        assertThat(removed.body()).contains("\"discount\":0.00").contains("\"total\":2100.00");
    }

    @Test
    void invalidCouponsAreRejectedWithAClearCode() throws Exception {
        Item item = sellable("500.00", "5.00", 50, true);
        String customer = newCustomer();

        Response emptyCart = as(customer, "POST", CART + "/coupon", "{\"code\":\"ANYTHING\"}");
        assertThat(emptyCart.status()).isEqualTo(422);
        assertThat(emptyCart.body()).contains("CART_EMPTY");

        add(customer, item.variantId(), 2);

        Response unknown = as(customer, "POST", CART + "/coupon", "{\"code\":\"NOPE-" + unique("") + "\"}");
        assertThat(unknown.status()).isEqualTo(422);
        assertThat(unknown.body()).contains("COUPON_INVALID");

        String bigMinimum = coupon("FIXED_AMOUNT", "100", "5000");
        Response belowMinimum = as(customer, "POST", CART + "/coupon", "{\"code\":\"" + bigMinimum + "\"}");
        assertThat(belowMinimum.status()).isEqualTo(422);
        assertThat(belowMinimum.body()).contains("COUPON_MIN_ORDER_NOT_MET");
    }

    // ---------- shipping ----------

    @Test
    void shippingIsCalculatedForTheDefaultAddressFromTheParcelWeight() throws Exception {
        Item item = sellable("500.00", "5.00", 50, true);
        String customer = newCustomer();
        String address = addAddress(customer, "122001");

        // 2 x 180 g = 360 g -> NCR slab 0-500 g = INR 50; goods 1000 + GST 50 + shipping 50
        Response cart = add(customer, item.variantId(), 2);

        assertThat(cart.body()).contains("\"shippingCalculated\":true").contains("\"shippingCharge\":50.00")
                .contains("\"total\":1100.00").contains("\"zoneName\":\"Delhi NCR\"")
                .contains("\"shippingAddressId\":\"" + address + "\"");
    }

    @Test
    void shippingIsFreeAboveTheZoneThreshold() throws Exception {
        Item item = sellable("1000.00", "5.00", 50, true);
        String customer = newCustomer();
        addAddress(customer, "122001");

        // goods 2000 >= 1999 (sample NCR threshold): shipping free; GST 100
        Response cart = add(customer, item.variantId(), 2);

        assertThat(cart.body()).contains("\"shippingCharge\":0.00").contains("\"freeShippingApplied\":true")
                .contains("\"total\":2100.00");
    }

    @Test
    void anUndeliverablePinIsReportedWithoutBreakingTheCart() throws Exception {
        Item item = sellable("500.00", "5.00", 50, true);
        String customer = newCustomer();
        addAddress(customer, "900001");

        Response cart = add(customer, item.variantId(), 2);

        assertThat(cart.status()).isEqualTo(200);
        assertThat(cart.body()).contains("\"shippingCalculated\":false")
                .contains("\"shippingError\":\"We do not deliver to this location yet.\"")
                .contains("\"total\":1050.00");
    }

    @Test
    void anotherCustomersAddressIsNotFound() throws Exception {
        String owner = newCustomer();
        String address = addAddress(owner, "122001");
        String stranger = newCustomer();

        Response response = as(stranger, "GET", CART + "?addressId=" + address, null);

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).contains("ADDRESS_NOT_FOUND");
    }

    // ---------- concurrency ----------

    @Test
    void parallelFirstAddsNeitherDuplicateTheLineNorLoseQuantity() throws Exception {
        Item item = sellable("500.00", "5.00", 100, true);
        String customer = newCustomer();
        int threads = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Response>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> add(customer, item.variantId(), 1)));
            }
            for (Future<Response> future : futures) {
                Response response = future.get(60, TimeUnit.SECONDS);
                assertThat(response.status()).as("parallel add: %s", response.body()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        Response cart = as(customer, "GET", CART, null);
        assertThat(count(cart.body(), "\"itemId\"")).isEqualTo(1);
        assertThat(cart.body()).contains("\"quantity\":8");
    }
}