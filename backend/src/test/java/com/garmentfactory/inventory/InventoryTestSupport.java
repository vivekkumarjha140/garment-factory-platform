package com.garmentfactory.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared fixtures for the inventory integration tests: admin login, catalog variants, stock, staff users. */
final class InventoryTestSupport {

    static final String CATALOG = "/api/v1/admin/catalog";
    static final String INVENTORY = "/api/v1/inventory";
    static final String ADMIN_EMAIL = "admin@example.com";
    static final String ADMIN_PASSWORD = "Adm1nStrongPass";
    private static final String STAFF_PASSWORD = "Str0ngPassw0rd";

    private final ApiClient api;
    private final JdbcTemplate jdbc;
    private final String adminToken;

    InventoryTestSupport(Environment environment, JdbcTemplate jdbc) throws Exception {
        this.api = new ApiClient(environment);
        this.jdbc = jdbc;
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson(ADMIN_EMAIL, ADMIN_PASSWORD));
        assertThat(login.status()).as("bootstrap admin login: %s", login.body()).isEqualTo(200);
        this.adminToken = login.accessToken();
    }

    ApiClient api() {
        return api;
    }

    static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ---------- HTTP ----------

    Response as(String token, String method, String path, String body) throws Exception {
        return api.send(method, path, body, Map.of("Authorization", "Bearer " + token));
    }

    Response admin(String method, String path, String body) throws Exception {
        return as(adminToken, method, path, body);
    }

    static Response expect(Response response, int status, String what) {
        assertThat(response.status()).as("%s: %s", what, response.body()).isEqualTo(status);
        return response;
    }

    static String field(Response response, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(response.body());
        assertThat(matcher.find()).as("\"%s\" in %s", name, response.body()).isTrue();
        return matcher.group(1);
    }

    // ---------- fixtures ----------

    /** Creates category, product and variant through the catalog admin API; returns the variant's internal id. */
    long createVariant() throws Exception {
        String category = field(expect(admin("POST", CATALOG + "/categories",
                "{\"name\":\"" + unique("Cat") + "\",\"parentId\":null}"), 201, "create category"), "id");
        String product = field(expect(admin("POST", CATALOG + "/products",
                "{\"categoryId\":\"" + category + "\",\"brandId\":null,\"name\":\"" + unique("Tee ")
                        + "\",\"description\":\"Soft cotton tee\",\"fabric\":\"Cotton\",\"gsm\":180,"
                        + "\"composition\":\"100% cotton\",\"hsnCode\":\"6109\",\"gstRate\":5.00,"
                        + "\"basePrice\":499.00,\"mrp\":699.00,\"customizable\":true}"), 201, "create product"), "id");
        String variant = field(expect(admin("POST", CATALOG + "/products/" + product + "/variants",
                "{\"sku\":\"" + unique("SKU-") + "\",\"size\":\"M\",\"colorName\":\"Red\",\"colorHex\":\"#FF0000\","
                        + "\"priceOverride\":null,\"weightG\":180,\"barcode\":null}"), 201, "add variant"), "id");
        return jdbc.queryForObject("SELECT id FROM product_variants WHERE public_id = ?", Long.class, variant);
    }

    /** A variant with a stock row and the given on-hand quantity (received through the API). */
    long createStockedVariant(int onHand) throws Exception {
        long variantId = createVariant();
        expect(admin("POST", INVENTORY + "/stock", "{\"variantId\":" + variantId + ",\"lowStockThreshold\":2}"),
                201, "initialise stock");
        if (onHand > 0) {
            expect(admin("POST", INVENTORY + "/stock/" + variantId + "/receipts", "{\"quantity\":" + onHand + "}"),
                    201, "receive stock");
        }
        return variantId;
    }

    /** Creates a staff user with the given role and returns an access token for it. */
    String newStaff(String role, CapturingEmailSender emails) throws Exception {
        String email = "staff-" + UUID.randomUUID() + "@example.com";
        expect(admin("POST", "/api/v1/admin/users",
                        "{\"fullName\":\"Staff Member\",\"email\":\"" + email + "\",\"roles\":[\"" + role + "\"]}"),
                201, "create staff user");
        String token = emails.latestTokenTo(email);
        assertThat(api.post("/api/v1/auth/reset-password",
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + STAFF_PASSWORD + "\"}").status()).isEqualTo(204);
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson(email, STAFF_PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        return login.accessToken();
    }

    // ---------- database reads ----------

    int onHand(long variantId) {
        return jdbc.queryForObject("SELECT on_hand FROM finished_goods_stock WHERE variant_id = ?", Integer.class, variantId);
    }

    int reserved(long variantId) {
        return jdbc.queryForObject("SELECT reserved FROM finished_goods_stock WHERE variant_id = ?", Integer.class, variantId);
    }

    int damaged(long variantId) {
        return jdbc.queryForObject("SELECT damaged FROM finished_goods_stock WHERE variant_id = ?", Integer.class, variantId);
    }

    int ledgerCount(long variantId, String movementType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE variant_id = ? AND movement_type = ?",
                Integer.class, variantId, movementType);
    }

    int reservationCount(long variantId, String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stock_reservations WHERE variant_id = ? AND status = ?",
                Integer.class, variantId, status);
    }
}