package com.garmentfactory.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.BytesResponse;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
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

/**
 * The public shop API and product images against a real MySQL 8.4. Data is created through the staff
 * API, then read back anonymously. Requires Docker.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin"})
class PublicCatalogApiIT {

    private static final String ADMIN_CATALOG = "/api/v1/admin/catalog";
    private static final String SHOP = "/api/v1/catalog";
    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final Pattern ID = Pattern.compile("\"id\":\"([^\"]+)\"");
    private static final Pattern SLUG = Pattern.compile("\"slug\":\"([^\"]+)\"");

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private Environment environment;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CapturingEmailSender emails;

    private ApiClient api;
    private String admin;

    private record Created(String id, String slug) {
    }

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

    private Response shop(String path) throws Exception {
        return api.get(SHOP + path, null);
    }

    private static Created created(Response response) {
        Matcher id = ID.matcher(response.body());
        Matcher slug = SLUG.matcher(response.body());

        assertThat(id.find())
                .as("id in %s", response.body())
                .isTrue();

        String slugValue = slug.find() ? slug.group(1) : null;
        return new Created(id.group(1), slugValue);
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Created category(String name, String parentId) throws Exception {
        String parent = parentId == null ? "null" : "\"" + parentId + "\"";
        Response response = as(admin, "POST", ADMIN_CATALOG + "/categories", "{\"name\":\"" + name + "\",\"parentId\":" + parent + "}");
        assertThat(response.status()).as("create category: %s", response.body()).isEqualTo(201);
        return created(response);
    }

    private String brand(String name) throws Exception {
        Response response = as(admin, "POST", ADMIN_CATALOG + "/brands", "{\"name\":\"" + name + "\"}");
        assertThat(response.status()).isEqualTo(201);
        return created(response).id();
    }

    private Created product(String categoryId, String brandId, String name, String base, String mrp, boolean customizable) throws Exception {
        String brandJson = brandId == null ? "null" : "\"" + brandId + "\"";
        Response response = as(admin, "POST", ADMIN_CATALOG + "/products",
                "{\"categoryId\":\"" + categoryId + "\",\"brandId\":" + brandJson + ",\"name\":\"" + name
                        + "\",\"description\":\"Soft cotton\",\"fabric\":\"Cotton\",\"gsm\":180,\"composition\":\"100% cotton\","
                        + "\"hsnCode\":\"6109\",\"gstRate\":5.00,\"basePrice\":" + base + ",\"mrp\":" + mrp
                        + ",\"customizable\":" + customizable + "}");
        assertThat(response.status()).as("create product: %s", response.body()).isEqualTo(201);
        return created(response);
    }

    private String variant(String productId, String size, String color, String override) throws Exception {
        Response response = as(admin, "POST", ADMIN_CATALOG + "/products/" + productId + "/variants",
                "{\"sku\":\"" + unique("SKU-") + "\",\"size\":\"" + size + "\",\"colorName\":\"" + color
                        + "\",\"colorHex\":\"#112233\",\"priceOverride\":" + override + ",\"weightG\":180,\"barcode\":null}");
        assertThat(response.status()).as("add variant: %s", response.body()).isEqualTo(201);
        return created(response).id();
    }

    private void publish(String productId) throws Exception {
        assertThat(as(admin, "POST", ADMIN_CATALOG + "/products/" + productId + "/status", "{\"status\":\"ACTIVE\"}").status())
                .isEqualTo(200);
    }

    private String newStaff(String role) throws Exception {
        String email = "staff-" + UUID.randomUUID() + "@example.com";
        assertThat(as(admin, "POST", "/api/v1/admin/users",
                "{\"fullName\":\"Staff Member\",\"email\":\"" + email + "\",\"roles\":[\"" + role + "\"]}").status()).isEqualTo(201);
        assertThat(api.post("/api/v1/auth/reset-password",
                "{\"token\":\"" + emails.latestTokenTo(email) + "\",\"newPassword\":\"" + PASSWORD + "\"}").status()).isEqualTo(204);
        return api.post("/api/v1/auth/login", ApiClient.loginJson(email, PASSWORD)).accessToken();
    }

    private static byte[] image(int width, int height, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    private Response upload(String productId, byte[] bytes, String fileName, String contentType, Map<String, String> fields) throws Exception {
        return api.postMultipart(ADMIN_CATALOG + "/products/" + productId + "/images", admin, fileName, contentType, bytes, fields);
    }

    private static int position(Response response, Created product) {
        return response.body().indexOf("\"slug\":\"" + product.slug() + "\"");
    }

    // ---------- visibility ----------

    @Test
    void onlyPublishedProductsAreVisibleToShoppers() throws Exception {
        String token = unique("vis").toLowerCase();
        Created category = category(unique("Cat"), null);
        Created product = product(category.id(), null, "Visible " + token, "499.00", "699.00", false);
        String active = variant(product.id(), "M", "Red", "null");

        assertThat(shop("/products?q=" + token).body()).contains("\"totalElements\":0");
        assertThat(shop("/products/" + product.slug()).status()).isEqualTo(404);

        publish(product.id());
        assertThat(shop("/products?q=" + token).body()).contains(product.slug());
        Response detail = shop("/products/" + product.slug());
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body()).contains("Visible " + token).contains(active);

        assertThat(as(admin, "POST", ADMIN_CATALOG + "/products/" + product.id() + "/status", "{\"status\":\"INACTIVE\"}").status())
                .isEqualTo(200);
        assertThat(shop("/products?q=" + token).body()).contains("\"totalElements\":0");
        assertThat(shop("/products/" + product.slug()).status()).isEqualTo(404);
    }

    @Test
    void theShopNeverExposesInternalFields() throws Exception {
        Created category = category(unique("Cat"), null);
        Created product = product(category.id(), null, unique("Tee "), "499.00", "699.00", false);
        variant(product.id(), "M", "Red", "null");
        publish(product.id());

        Response detail = shop("/products/" + product.slug());

        assertThat(detail.body()).doesNotContain("\"sku\"").doesNotContain("hsnCode").doesNotContain("barcode")
                .doesNotContain("\"status\"").contains("\"gstRate\":5.00");
    }

    @Test
    void categoriesAndTheirProductsDisappearWhenACategoryOrItsParentIsDeactivated() throws Exception {
        String token = unique("cv").toLowerCase();
        Created root = category(unique("Root"), null);
        Created child = category(unique("Child"), root.id());
        Created product = product(child.id(), null, "Hidden " + token, "499.00", "699.00", false);
        variant(product.id(), "M", "Red", "null");
        publish(product.id());
        assertThat(shop("/categories").body()).contains(root.slug()).contains(child.slug());
        assertThat(shop("/products?q=" + token).body()).contains(product.slug());

        assertThat(as(admin, "PUT", ADMIN_CATALOG + "/categories/" + root.id(),
                "{\"name\":\"Root\",\"active\":false}").status()).isEqualTo(200);

        assertThat(shop("/categories").body()).doesNotContain(root.slug()).doesNotContain(child.slug());
        assertThat(shop("/products?q=" + token).body()).contains("\"totalElements\":0");
        assertThat(shop("/products/" + product.slug()).status()).isEqualTo(404);
        assertThat(shop("/products?category=" + child.slug()).status()).isEqualTo(404);

        assertThat(as(admin, "PUT", ADMIN_CATALOG + "/categories/" + root.id(),
                "{\"name\":\"Root\",\"active\":true}").status()).isEqualTo(200);
        assertThat(shop("/products?q=" + token).body()).contains(product.slug());
    }

    // ---------- search, filters, sorting, paging ----------

    @Test
    void listingSupportsSearchCategoryBrandPriceSizeColourAndCustomizableFilters() throws Exception {
        String t = unique("f").toLowerCase();
        Created men = category("Men" + t, null);
        Created tops = category("Tops" + t, men.id());
        String brand1 = brand("Alpha" + t);
        String brand2 = brand("Beta" + t);

        Created a = product(tops.id(), brand1, "Alpha tee " + t, "400.00", "600.00", false);
        variant(a.id(), "M", "Red", "null");
        variant(a.id(), "L", "Blue", "350.00");
        Created b = product(men.id(), brand1, "Beta polo " + t, "800.00", "900.00", false);
        variant(b.id(), "XL", "Green", "null");
        Created d = product(tops.id(), brand2, "Gamma shirt " + t, "600.00", "700.00", true);
        variant(d.id(), "M", "Red", "null");
        publish(a.id());
        publish(b.id());
        publish(d.id());
        String base = "/products?q=" + t;

        Response all = shop(base);
        assertThat(all.body()).contains(a.slug()).contains(b.slug()).contains(d.slug()).contains("\"totalElements\":3");
        assertThat(all.body()).contains("\"fromPrice\":350.00");

        assertThat(shop(base + "&category=" + men.slug()).body()).contains("\"totalElements\":3");
        Response inTops = shop(base + "&category=" + tops.slug());
        assertThat(inTops.body()).contains(a.slug()).contains(d.slug()).doesNotContain(b.slug());

        Response byBrand = shop(base + "&brand=" + brand1);
        assertThat(byBrand.body()).contains(a.slug()).contains(b.slug()).doesNotContain(d.slug());

        Response expensive = shop(base + "&minPrice=500");
        assertThat(expensive.body()).contains(b.slug()).contains(d.slug()).doesNotContain(a.slug());
        Response cheap = shop(base + "&maxPrice=450");
        assertThat(cheap.body()).contains(a.slug()).doesNotContain(b.slug()).doesNotContain(d.slug());

        assertThat(shop(base + "&sizes=L").body()).contains(a.slug()).doesNotContain(b.slug()).doesNotContain(d.slug());
        assertThat(shop(base + "&colors=Blue").body()).contains(a.slug()).doesNotContain(d.slug());
        assertThat(shop(base + "&sizes=M&sizes=XL").body()).contains("\"totalElements\":3");
        // Size and colour must belong to the same variant: nothing is a Blue in size M.
        assertThat(shop(base + "&sizes=M&colors=Blue").body()).contains("\"totalElements\":0");

        Response custom = shop(base + "&customizable=true");
        assertThat(custom.body()).contains(d.slug()).doesNotContain(a.slug());
    }

    @Test
    void invalidFiltersAreRejectedWithClearErrors() throws Exception {
        assertThat(shop("/products?category=no-such-category").status()).isEqualTo(404);
        assertThat(shop("/products?sizes=Giant").body()).contains("INVALID_SIZE");
        assertThat(shop("/products?minPrice=10&maxPrice=5").body()).contains("INVALID_PRICE_RANGE");
        assertThat(shop("/products?minPrice=-1").status()).isEqualTo(400);
        assertThat(shop("/products?sort=bogus").body()).contains("INVALID_SORT");
        assertThat(shop("/products?brand=" + UUID.randomUUID()).body()).contains("UNKNOWN_BRAND");
        assertThat(shop("/products?size=1000").body()).contains("\"size\":100");
    }

    @Test
    void sortingOrdersByPriceNameAndNewest() throws Exception {
        Created category = category(unique("Sort"), null);
        Created charlie = product(category.id(), null, "Charlie tee", "300.00", "600.00", false);
        Created alpha = product(category.id(), null, "Alpha tee", "500.00", "600.00", false);
        Created bravo = product(category.id(), null, "Bravo tee", "400.00", "600.00", false);
        for (Created p : new Created[]{charlie, alpha, bravo}) {
            variant(p.id(), "M", "Red", "null");
            publish(p.id());
        }
        String base = "/products?category=" + category.slug();

        Response priceAsc = shop(base + "&sort=price_asc");
        assertThat(position(priceAsc, charlie)).isLessThan(position(priceAsc, bravo));
        assertThat(position(priceAsc, bravo)).isLessThan(position(priceAsc, alpha));

        Response priceDesc = shop(base + "&sort=price_desc");
        assertThat(position(priceDesc, alpha)).isLessThan(position(priceDesc, bravo));
        assertThat(position(priceDesc, bravo)).isLessThan(position(priceDesc, charlie));

        Response byName = shop(base + "&sort=name");
        assertThat(position(byName, alpha)).isLessThan(position(byName, bravo));
        assertThat(position(byName, bravo)).isLessThan(position(byName, charlie));

        Response newest = shop(base);
        assertThat(position(newest, bravo)).isLessThan(position(newest, alpha));
        assertThat(position(newest, alpha)).isLessThan(position(newest, charlie));
    }

    @Test
    void resultsArePaged() throws Exception {
        String t = unique("pg").toLowerCase();
        Created category = category(unique("Cat"), null);
        for (int i = 0; i < 3; i++) {
            Created p = product(category.id(), null, "Paged " + t + " " + i, "499.00", "699.00", false);
            variant(p.id(), "M", "Red", "null");
            publish(p.id());
        }

        Response first = shop("/products?q=" + t + "&size=2&page=0");
        Response second = shop("/products?q=" + t + "&size=2&page=1");
        Response beyond = shop("/products?q=" + t + "&size=2&page=5");

        assertThat(first.body()).contains("\"totalElements\":3").contains("\"totalPages\":2").contains("\"size\":2");
        assertThat(first.body().split("\"slug\"", -1).length - 1).isEqualTo(2);
        assertThat(second.body().split("\"slug\"", -1).length - 1).isEqualTo(1);
        assertThat(beyond.status()).isEqualTo(200);
        assertThat(beyond.body()).contains("\"items\":[]");
    }

    // ---------- product detail and facets ----------

    @Test
    void detailShowsOnlyActiveVariantsTheirPricesAndTheQuantityTiers() throws Exception {
        Created category = category(unique("Cat"), null);
        Created product = product(category.id(), null, unique("Tee "), "500.00", "699.00", true);
        variant(product.id(), "M", "Red", "null");
        String blue = variant(product.id(), "L", "Blue", "450.00");
        String green = variant(product.id(), "XL", "Green", "null");
        publish(product.id());
        assertThat(as(admin, "PUT", ADMIN_CATALOG + "/products/" + product.id() + "/price-tiers",
                "{\"tiers\":[{\"minQty\":10,\"maxQty\":null,\"unitPrice\":400.00}]}").status()).isEqualTo(200);
        assertThat(as(admin, "POST", ADMIN_CATALOG + "/products/" + product.id() + "/variants/" + green + "/status",
                "{\"status\":\"INACTIVE\"}").status()).isEqualTo(200);

        Response detail = shop("/products/" + product.slug());

        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body()).contains("\"colorName\":\"Red\"").contains("\"colorName\":\"Blue\"")
                .doesNotContain("Green").contains("\"price\":450.00").contains("\"fromPrice\":450.00")
                .contains("\"minQty\":10").contains("\"unitPrice\":400.00").contains(blue);
    }

    @Test
    void facetsListTheAvailableSizesColoursAndPriceRange() throws Exception {
        Created category = category(unique("Facet"), null);
        Created product = product(category.id(), null, unique("Tee "), "400.00", "600.00", false);
        variant(product.id(), "L", "Blue", "350.00");
        variant(product.id(), "M", "Red", "null");
        publish(product.id());

        Response facets = shop("/facets?category=" + category.slug());

        assertThat(facets.status()).isEqualTo(200);
        assertThat(facets.body()).contains("\"sizes\":[\"M\",\"L\"]").contains("\"name\":\"Blue\"").contains("\"name\":\"Red\"")
                .contains("\"minPrice\":350.00").contains("\"maxPrice\":400.00");
    }

    @Test
    void theShopApiNeedsNoSignIn() throws Exception {
        assertThat(shop("/categories").status()).isEqualTo(200);
        assertThat(shop("/brands").status()).isEqualTo(200);
        assertThat(shop("/products").status()).isEqualTo(200);
        assertThat(shop("/facets").status()).isEqualTo(200);
    }

    // ---------- images ----------

    @Test
    void imageLifecycleUploadServePrimaryAndDelete() throws Exception {
        String t = unique("img").toLowerCase();
        Created category = category(unique("Cat"), null);
        Created product = product(category.id(), null, "Image " + t, "499.00", "699.00", false);
        variant(product.id(), "M", "Red", "null");
        byte[] png = image(300, 300, "png");
        byte[] jpeg = image(400, 400, "jpg");

        Response first = upload(product.id(), png, "front.png", "image/png", Map.of("altText", "Front view"));
        assertThat(first.status()).as("first upload: %s", first.body()).isEqualTo(201);
        assertThat(first.body()).contains("\"primary\":true").contains("Front view");
        String firstId = created(first).id();
        // The file name says png, the bytes are a JPEG: the bytes win and nothing else is trusted.
        Response second = upload(product.id(), jpeg, "second.png", "image/png", Map.of());
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.body()).contains("\"primary\":false");
        String secondId = created(second).id();

        assertThat(api.getBytes(SHOP + "/images/" + firstId, null).status()).isEqualTo(404);
        BytesResponse preview = api.getBytes(ADMIN_CATALOG + "/images/" + firstId, admin);
        assertThat(preview.status()).isEqualTo(200);
        assertThat(Arrays.equals(preview.body(), png)).isTrue();

        publish(product.id());
        BytesResponse publicPng = api.getBytes(SHOP + "/images/" + firstId, null);
        assertThat(publicPng.status()).isEqualTo(200);
        assertThat(publicPng.header("content-type")).isEqualTo("image/png");
        assertThat(publicPng.header("cache-control")).contains("immutable").contains("public");
        assertThat(Arrays.equals(publicPng.body(), png)).isTrue();
        assertThat(api.getBytes(SHOP + "/images/" + secondId, null).header("content-type")).isEqualTo("image/jpeg");
        assertThat(shop("/products?q=" + t).body()).contains("/api/v1/catalog/images/" + firstId);

        Response moved = as(admin, "PUT", ADMIN_CATALOG + "/products/" + product.id() + "/images/" + secondId, "{\"primary\":true}");
        assertThat(moved.status()).isEqualTo(200);
        assertThat(moved.body()).contains("\"primary\":true");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE is_primary = 1 AND product_id = "
                + "(SELECT id FROM products WHERE public_id = ?)", Integer.class, product.id())).isEqualTo(1);

        assertThat(as(admin, "DELETE", ADMIN_CATALOG + "/products/" + product.id() + "/images/" + secondId, null).status()).isEqualTo(204);
        assertThat(api.getBytes(SHOP + "/images/" + secondId, null).status()).isEqualTo(404);
        Response remaining = as(admin, "GET", ADMIN_CATALOG + "/products/" + product.id() + "/images", null);
        assertThat(remaining.body()).contains(firstId).contains("\"primary\":true").doesNotContain(secondId);
    }

    @Test
    void invalidUploadsAreRejected() throws Exception {
        Created category = category(unique("Cat"), null);
        Created product = product(category.id(), null, unique("Tee "), "499.00", "699.00", false);
        byte[] png = image(300, 300, "png");

        Response notAnImage = upload(product.id(), "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8),
                "evil.png", "image/png", Map.of());
        assertThat(notAnImage.status()).isEqualTo(400);
        assertThat(notAnImage.body()).contains("INVALID_IMAGE");

        assertThat(upload(product.id(), image(300, 300, "gif"), "pic.png", "image/png", Map.of()).body()).contains("INVALID_IMAGE");
        assertThat(upload(product.id(), image(100, 100, "png"), "small.png", "image/png", Map.of()).body()).contains("at least");
        assertThat(upload(product.id(), new byte[0], "empty.png", "image/png", Map.of()).status()).isEqualTo(400);

        Response unknownVariant = upload(product.id(), png, "x.png", "image/png", Map.of("variantId", UUID.randomUUID().toString()));
        assertThat(unknownVariant.status()).isEqualTo(400);
        assertThat(unknownVariant.body()).contains("UNKNOWN_VARIANT");

        Response unknownProduct = api.postMultipart(ADMIN_CATALOG + "/products/" + UUID.randomUUID() + "/images", admin,
                "x.png", "image/png", png, Map.of());
        assertThat(unknownProduct.status()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE product_id = "
                + "(SELECT id FROM products WHERE public_id = ?)", Integer.class, product.id())).isZero();
    }

    @Test
    void aProductCanHaveAtMostTenImages() throws Exception {
        Created category = category(unique("Cat"), null);
        Created product = product(category.id(), null, unique("Tee "), "499.00", "699.00", false);
        byte[] png = image(200, 200, "png");
        for (int i = 0; i < 10; i++) {
            assertThat(upload(product.id(), png, "i" + i + ".png", "image/png", Map.of()).status()).isEqualTo(201);
        }

        Response eleventh = upload(product.id(), png, "extra.png", "image/png", Map.of());

        assertThat(eleventh.status()).isEqualTo(409);
        assertThat(eleventh.body()).contains("IMAGE_LIMIT_REACHED");
    }

    @Test
    void imagePermissionsFollowTheCatalogPermissions() throws Exception {
        Created category = category(unique("Cat"), null);
        Created product = product(category.id(), null, unique("Tee "), "499.00", "699.00", false);
        byte[] png = image(300, 300, "png");
        String designer = newStaff("DESIGNER");

        Response forbidden = api.postMultipart(ADMIN_CATALOG + "/products/" + product.id() + "/images", designer,
                "x.png", "image/png", png, Map.of());
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(as(designer, "GET", ADMIN_CATALOG + "/products/" + product.id() + "/images", null).status()).isEqualTo(200);
        assertThat(as(designer, "DELETE", ADMIN_CATALOG + "/products/" + product.id() + "/images/" + UUID.randomUUID(), null).status())
                .isEqualTo(403);
        assertThat(api.get(ADMIN_CATALOG + "/products/" + product.id() + "/images", null).status()).isEqualTo(401);
    }
}