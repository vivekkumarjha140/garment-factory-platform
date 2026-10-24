package com.garmentfactory.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import java.util.Map;
import java.util.UUID;
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

/** Catalog administration against a real MySQL 8.4, with a bootstrapped administrator. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin"})
class CatalogAdminApiIT {

    private static final String CATALOG = "/api/v1/admin/catalog";
    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final Pattern ID = Pattern.compile("\"id\":\"([^\"]+)\"");

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

    private Response as(String token, String method, String path, String body) throws Exception {
        return api.send(method, path, body, Map.of("Authorization", "Bearer " + token));
    }

    private static String firstId(Response response) {
        Matcher matcher = ID.matcher(response.body());
        assertThat(matcher.find()).as("id in %s", response.body()).isTrue();
        return matcher.group(1);
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String createCategory(String name, String parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        Response created = as(admin, "POST", CATALOG + "/categories", "{\"name\":\"" + name + "\",\"parentId\":" + parent + "}");
        assertThat(created.status()).as("create category: %s", created.body()).isEqualTo(201);
        return firstId(created);
    }

    private static String productJson(String categoryId, String name, String gst, String base, String mrp) {
        return "{\"categoryId\":\"" + categoryId + "\",\"brandId\":null,\"name\":\"" + name
                + "\",\"description\":\"Soft cotton tee\",\"fabric\":\"Cotton\",\"gsm\":180,\"composition\":\"100% cotton\","
                + "\"hsnCode\":\"6109\",\"gstRate\":" + gst + ",\"basePrice\":" + base + ",\"mrp\":" + mrp + ",\"customizable\":true}";
    }

    private String createProduct(String name, String base, String mrp) throws Exception {
        String category = createCategory(unique("Cat"), null);
        Response created = as(admin, "POST", CATALOG + "/products", productJson(category, name, "5.00", base, mrp));
        assertThat(created.status()).as("create product: %s", created.body()).isEqualTo(201);
        return firstId(created);
    }

    private static String variantJson(String sku, String size, String color) {
        return "{\"sku\":\"" + sku + "\",\"size\":\"" + size + "\",\"colorName\":\"" + color
                + "\",\"colorHex\":\"#FF0000\",\"priceOverride\":null,\"weightG\":180,\"barcode\":null}";
    }

    private Response addVariant(String productId, String sku, String size, String color) throws Exception {
        return as(admin, "POST", CATALOG + "/products/" + productId + "/variants", variantJson(sku, size, color));
    }

    private String addActiveVariant(String productId) throws Exception {
        Response variant = addVariant(productId, unique("SKU-"), "M", "Red");
        assertThat(variant.status()).as("add variant: %s", variant.body()).isEqualTo(201);
        return firstId(variant);
    }

    private Response setProductStatus(String productId, String status) throws Exception {
        return as(admin, "POST", CATALOG + "/products/" + productId + "/status", "{\"status\":\"" + status + "\"}");
    }

    private String newStaff(String role) throws Exception {
        String email = "staff-" + UUID.randomUUID() + "@example.com";
        Response created = as(admin, "POST", "/api/v1/admin/users",
                "{\"fullName\":\"Staff Member\",\"email\":\"" + email + "\",\"roles\":[\"" + role + "\"]}");
        assertThat(created.status()).isEqualTo(201);
        String token = emails.latestTokenTo(email);
        assertThat(api.post("/api/v1/auth/reset-password",
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + PASSWORD + "\"}").status()).isEqualTo(204);
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson(email, PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        return login.accessToken();
    }

    private int auditCount(String action, String entityId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = ? AND entity_id = ?",
                Integer.class, action, entityId);
    }

    // ---------- categories and brands ----------

    @Test
    void categoriesFormATreeWithUniqueSlugsAndADepthLimit() throws Exception {
        String name = unique("Shirts");
        String root = createCategory(name, null);
        Response second = as(admin, "POST", CATALOG + "/categories", "{\"name\":\"" + name + "\"}");
        assertThat(second.body()).contains("\"slug\":\"" + name.toLowerCase() + "-2\"");

        String child = createCategory("Tops", root);
        String grandchild = createCategory("Printed", child);

        Response tooDeep = as(admin, "POST", CATALOG + "/categories", "{\"name\":\"Too deep\",\"parentId\":\"" + grandchild + "\"}");
        assertThat(tooDeep.status()).isEqualTo(400);
        assertThat(tooDeep.body()).contains("CATEGORY_TOO_DEEP");

        Response cycle = as(admin, "PUT", CATALOG + "/categories/" + root,
                "{\"name\":\"" + name + "\",\"parentId\":\"" + grandchild + "\"}");
        assertThat(cycle.status()).isEqualTo(400);
        assertThat(cycle.body()).contains("CATEGORY_CYCLE");

        assertThat(as(admin, "GET", CATALOG + "/categories", null).body()).contains("\"depth\":3");
    }

    @Test
    void brandNamesAreUniqueIgnoringCase() throws Exception {
        String name = unique("Brand");
        assertThat(as(admin, "POST", CATALOG + "/brands", "{\"name\":\"" + name + "\"}").status()).isEqualTo(201);

        Response duplicate = as(admin, "POST", CATALOG + "/brands", "{\"name\":\"" + name.toUpperCase() + "\"}");

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body()).contains("BRAND_ALREADY_EXISTS");
    }

    // ---------- products ----------

    @Test
    void aProductStartsAsDraftAndCanOnlyBePublishedWithAnActiveVariant() throws Exception {
        String product = createProduct(unique("Tee "), "499.00", "699.00");
        assertThat(as(admin, "GET", CATALOG + "/products/" + product, null).body()).contains("\"status\":\"DRAFT\"");

        Response early = setProductStatus(product, "ACTIVE");
        assertThat(early.status()).isEqualTo(409);
        assertThat(early.body()).contains("NO_ACTIVE_VARIANT");

        addActiveVariant(product);
        Response published = setProductStatus(product, "ACTIVE");
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body()).contains("\"status\":\"ACTIVE\"");
        assertThat(auditCount("PRODUCT_STATUS_CHANGED", product)).isEqualTo(1);
    }

    @Test
    void statusChangesFollowTheAllowedTransitions() throws Exception {
        String product = createProduct(unique("Tee "), "499.00", "699.00");
        addActiveVariant(product);

        Response draftToInactive = setProductStatus(product, "INACTIVE");
        assertThat(draftToInactive.status()).isEqualTo(409);
        assertThat(draftToInactive.body()).contains("INVALID_TRANSITION");

        assertThat(setProductStatus(product, "ARCHIVED").status()).isEqualTo(200);
        Response archivedToActive = setProductStatus(product, "ACTIVE");
        assertThat(archivedToActive.status()).isEqualTo(409);
        assertThat(setProductStatus(product, "DRAFT").status()).isEqualTo(200);
        assertThat(setProductStatus(product, "NOT_A_STATUS").status()).isEqualTo(400);
    }

    @Test
    void anArchivedProductCannotBeEditedOrGetNewVariants() throws Exception {
        String product = createProduct(unique("Tee "), "499.00", "699.00");
        setProductStatus(product, "ARCHIVED");

        Response variant = addVariant(product, unique("SKU-"), "M", "Red");
        assertThat(variant.status()).isEqualTo(409);
        assertThat(variant.body()).contains("PRODUCT_ARCHIVED");
        Response tiers = as(admin, "PUT", CATALOG + "/products/" + product + "/price-tiers", "{\"tiers\":[]}");
        assertThat(tiers.status()).isEqualTo(409);
    }

    @Test
    void priceRulesAreEnforced() throws Exception {
        String category = createCategory(unique("Cat"), null);

        Response mrpBelowPrice = as(admin, "POST", CATALOG + "/products", productJson(category, "Tee", "5.00", "500.00", "400.00"));
        assertThat(mrpBelowPrice.status()).isEqualTo(400);
        assertThat(mrpBelowPrice.body()).contains("INVALID_PRICE");

        Response negative = as(admin, "POST", CATALOG + "/products", productJson(category, "Tee", "5.00", "-1.00", "400.00"));
        assertThat(negative.status()).isEqualTo(400);
        assertThat(negative.body()).contains("VALIDATION_FAILED");

        Response gstTooHigh = as(admin, "POST", CATALOG + "/products", productJson(category, "Tee", "28.01", "500.00", "600.00"));
        assertThat(gstTooHigh.status()).isEqualTo(400);

        Response unknownCategory = as(admin, "POST", CATALOG + "/products",
                productJson(UUID.randomUUID().toString(), "Tee", "5.00", "500.00", "600.00"));
        assertThat(unknownCategory.status()).isEqualTo(400);
        assertThat(unknownCategory.body()).contains("UNKNOWN_CATEGORY");
    }

    @Test
    void priceChangesAreAuditedWithOldAndNewValues() throws Exception {
        String category = createCategory(unique("Cat"), null);
        Response created = as(admin, "POST", CATALOG + "/products", productJson(category, unique("Tee "), "5.00", "499.00", "699.00"));
        String product = firstId(created);

        Response updated = as(admin, "PUT", CATALOG + "/products/" + product, productJson(category, "Renamed tee", "5.00", "549.00", "699.00"));

        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body()).contains("Renamed tee").contains("\"basePrice\":549.00");
        assertThat(auditCount("PRODUCT_CREATED", product)).isEqualTo(1);
        String details = jdbc.queryForObject("SELECT details FROM audit_logs WHERE action = 'PRODUCT_UPDATED' AND entity_id = ?",
                String.class, product);
        assertThat(details).contains("499.00").contains("549.00");
    }

    @Test
    void productsCanBeSearchedByNameSkuAndStatus() throws Exception {
        String needle = unique("zzneedle").toLowerCase();
        String product = createProduct("Search target " + needle, "499.00", "699.00");
        String sku = "FIND-" + unique("X").toUpperCase();
        assertThat(addVariant(product, sku, "L", "Blue").status()).isEqualTo(201);

        assertThat(as(admin, "GET", CATALOG + "/products?q=" + needle, null).body()).contains(product);
        assertThat(as(admin, "GET", CATALOG + "/products?q=" + sku.toLowerCase(), null).body()).contains(product);
        assertThat(as(admin, "GET", CATALOG + "/products?q=" + needle + "&status=ACTIVE", null).body()).contains("\"totalElements\":0");
        assertThat(as(admin, "GET", CATALOG + "/products?q=" + needle + "&status=DRAFT", null).body()).contains(product);
        assertThat(as(admin, "GET", CATALOG + "/products?size=1000", null).body()).contains("\"size\":100");
    }

    // ---------- variants ----------

    @Test
    void skuAndVariantUniquenessRules() throws Exception {
        String product = createProduct(unique("Tee "), "499.00", "699.00");
        String sku = unique("tee-").toLowerCase();

        Response first = addVariant(product, sku, "xl", "Red");
        assertThat(first.status()).isEqualTo(201);
        assertThat(first.body()).contains("\"sku\":\"" + sku.toUpperCase() + "\"").contains("\"size\":\"XL\"");

        Response duplicateSku = addVariant(createProduct(unique("Other "), "499.00", "699.00"), sku.toUpperCase(), "M", "Blue");
        assertThat(duplicateSku.status()).isEqualTo(409);
        assertThat(duplicateSku.body()).contains("SKU_ALREADY_EXISTS");

        Response sameSizeAndColour = addVariant(product, unique("SKU-"), "XL", "red");
        assertThat(sameSizeAndColour.status()).isEqualTo(409);
        assertThat(sameSizeAndColour.body()).contains("VARIANT_ALREADY_EXISTS");

        Response badSize = addVariant(product, unique("SKU-"), "Giant", "Red");
        assertThat(badSize.status()).isEqualTo(400);
        assertThat(badSize.body()).contains("INVALID_SIZE");

        Response badSku = addVariant(product, "a b", "M", "Green");
        assertThat(badSku.status()).isEqualTo(400);
        assertThat(badSku.body()).contains("INVALID_SKU");
    }

    @Test
    void barcodesAreUniqueAndVariantDetailsCanBeUpdated() throws Exception {
        String product = createProduct(unique("Tee "), "499.00", "699.00");
        String barcode = unique("BC");
        Response first = as(admin, "POST", CATALOG + "/products/" + product + "/variants",
                variantJson(unique("SKU-"), "M", "Red").replace("\"barcode\":null", "\"barcode\":\"" + barcode + "\""));
        assertThat(first.status()).isEqualTo(201);
        String variantId = firstId(first);

        Response duplicate = as(admin, "POST", CATALOG + "/products/" + product + "/variants",
                variantJson(unique("SKU-"), "L", "Red").replace("\"barcode\":null", "\"barcode\":\"" + barcode + "\""));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body()).contains("BARCODE_ALREADY_EXISTS");

        Response updated = as(admin, "PUT", CATALOG + "/products/" + product + "/variants/" + variantId,
                "{\"size\":\"L\",\"colorName\":\"Crimson\",\"colorHex\":\"#DC143C\",\"priceOverride\":559.00,\"weightG\":190,\"barcode\":\"" + barcode + "\"}");
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body()).contains("\"size\":\"L\"").contains("Crimson").contains("\"effectivePrice\":559.00");
    }

    @Test
    void theLastActiveVariantOfAnActiveProductCannotBeDeactivated() throws Exception {
        String product = createProduct(unique("Tee "), "499.00", "699.00");
        String only = addActiveVariant(product);
        assertThat(setProductStatus(product, "ACTIVE").status()).isEqualTo(200);

        Response deactivate = as(admin, "POST", CATALOG + "/products/" + product + "/variants/" + only + "/status",
                "{\"status\":\"INACTIVE\"}");
        assertThat(deactivate.status()).isEqualTo(409);
        assertThat(deactivate.body()).contains("LAST_ACTIVE_VARIANT");

        Response secondVariant = addVariant(product, unique("SKU-"), "L", "Blue");
        assertThat(secondVariant.status())
                .as("add second variant: %s", secondVariant.body())
                .isEqualTo(201);
        String second = firstId(secondVariant);
        assertThat(as(admin, "POST", CATALOG + "/products/" + product + "/variants/" + only + "/status",
                "{\"status\":\"INACTIVE\"}").status()).isEqualTo(200);
        assertThat(as(admin, "POST", CATALOG + "/products/" + product + "/variants/" + second + "/status",
                "{\"status\":\"INACTIVE\"}").status()).isEqualTo(409);
    }

    // ---------- quantity price tiers ----------

    @Test
    void quantityPriceTiersAreValidatedAndReplacedAsAWhole() throws Exception {
        String product = createProduct(unique("Tee "), "500.00", "699.00");
        String path = CATALOG + "/products/" + product + "/price-tiers";

        Response ok = as(admin, "PUT", path, "{\"tiers\":[{\"minQty\":10,\"maxQty\":49,\"unitPrice\":450.00},"
                + "{\"minQty\":50,\"maxQty\":null,\"unitPrice\":400.00}]}");
        assertThat(ok.status()).isEqualTo(200);
        assertThat(as(admin, "GET", path, null).body()).contains("\"minQty\":10").contains("\"minQty\":50");

        Response overlap = as(admin, "PUT", path, "{\"tiers\":[{\"minQty\":10,\"maxQty\":49,\"unitPrice\":450.00},"
                + "{\"minQty\":40,\"maxQty\":null,\"unitPrice\":400.00}]}");
        assertThat(overlap.status()).isEqualTo(400);
        assertThat(overlap.body()).contains("INVALID_PRICE_TIERS");

        Response rising = as(admin, "PUT", path, "{\"tiers\":[{\"minQty\":10,\"maxQty\":49,\"unitPrice\":400.00},"
                + "{\"minQty\":50,\"maxQty\":null,\"unitPrice\":450.00}]}");
        assertThat(rising.status()).isEqualTo(400);

        Response aboveBase = as(admin, "PUT", path, "{\"tiers\":[{\"minQty\":10,\"maxQty\":null,\"unitPrice\":501.00}]}");
        assertThat(aboveBase.status()).isEqualTo(400);

        // A failed replacement leaves the previous tiers untouched.
        assertThat(as(admin, "GET", path, null).body()).contains("\"minQty\":50");
        assertThat(auditCount("PRICE_TIERS_REPLACED", product)).isEqualTo(1);
    }

    @Test
    void theBasePriceCannotDropBelowAnExistingTier() throws Exception {
        String category = createCategory(unique("Cat"), null);
        String product = firstId(as(admin, "POST", CATALOG + "/products", productJson(category, unique("Tee "), "5.00", "500.00", "699.00")));
        as(admin, "PUT", CATALOG + "/products/" + product + "/price-tiers",
                "{\"tiers\":[{\"minQty\":10,\"maxQty\":null,\"unitPrice\":450.00}]}");

        Response lowered = as(admin, "PUT", CATALOG + "/products/" + product, productJson(category, "Tee", "5.00", "400.00", "699.00"));

        assertThat(lowered.status()).isEqualTo(409);
        assertThat(lowered.body()).contains("PRICE_TIERS_CONFLICT");
    }

    // ---------- authorization ----------

    @Test
    void permissionsDecideWhoCanViewAndWhoCanChangeTheCatalog() throws Exception {
        String product = createProduct(unique("Tee "), "499.00", "699.00");
        String category = createCategory(unique("Cat"), null);
        String designer = newStaff("DESIGNER");
        String manager = newStaff("FACTORY_MANAGER");
        String body = productJson(category, unique("Tee "), "5.00", "499.00", "699.00");

        assertThat(api.get(CATALOG + "/products", null).status()).isEqualTo(401);

        assertThat(as(designer, "GET", CATALOG + "/products", null).status()).isEqualTo(200);
        assertThat(as(designer, "GET", CATALOG + "/products/" + product, null).status()).isEqualTo(200);
        assertThat(as(designer, "POST", CATALOG + "/products", body).status()).isEqualTo(403);
        assertThat(as(designer, "POST", CATALOG + "/categories", "{\"name\":\"X\"}").status()).isEqualTo(403);
        assertThat(as(designer, "PUT", CATALOG + "/products/" + product + "/price-tiers", "{\"tiers\":[]}").status()).isEqualTo(403);

        assertThat(as(manager, "POST", CATALOG + "/products", body).status()).isEqualTo(201);
        assertThat(as(manager, "POST", CATALOG + "/categories", "{\"name\":\"" + unique("Cat") + "\"}").status()).isEqualTo(201);
    }

    @Test
    void customersCannotUseTheStaffCatalogApi() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        Response registration = api.post("/api/v1/auth/register", ApiClient.registerJson(email, PASSWORD));
        assertThat(registration.status())
                .as("customer registration: %s", registration.body())
                .isEqualTo(202);

        String verificationToken = emails.latestTokenTo(email);
        Response verification = api.post("/api/v1/auth/verify-email",
                "{\"token\":\"" + verificationToken + "\"}");
        assertThat(verification.status())
                .as("customer email verification: %s", verification.body())
                .isIn(200, 204);

        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson(email, PASSWORD));
        assertThat(login.status())
                .as("customer login: %s", login.body())
                .isEqualTo(200);
        String customer = login.accessToken();
        String category = createCategory(unique("CustomerTestCat"), null);

        Response products = as(customer, "GET", CATALOG + "/products", null);
        assertThat(products.status())
                .as("customer GET products: %s", products.body())
                .isEqualTo(403);

        Response categories = as(customer, "GET", CATALOG + "/categories", null);
        assertThat(categories.status())
                .as("customer GET categories: %s", categories.body())
                .isEqualTo(403);

        Response createProduct = as(customer, "POST", CATALOG + "/products",
                """
                {
                  "name": "Test Product",
                  "categoryId": "%s",
                  "basePrice": 499.00,
                  "mrp": 699.00,
                  "gstRate": 5
                }
                """.formatted(category));

        assertThat(createProduct.status())
                .as("customer POST products: %s", createProduct.body())
                .isEqualTo(403);

    }
}