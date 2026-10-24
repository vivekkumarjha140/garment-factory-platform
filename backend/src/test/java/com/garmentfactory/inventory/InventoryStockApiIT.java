package com.garmentfactory.inventory;

import static com.garmentfactory.inventory.InventoryTestSupport.INVENTORY;
import static com.garmentfactory.inventory.InventoryTestSupport.expect;
import static com.garmentfactory.inventory.InventoryTestSupport.field;
import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

/** Inventory staff API against a real MySQL 8.4: ledger, permissions, audits. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin",
        "inventory.sweeper.enabled=false"})
class InventoryStockApiIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4")
                    .withCommand("--log-bin-trust-function-creators=1");

    @Autowired
    private Environment environment;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CapturingEmailSender emails;

    private InventoryTestSupport support;

    @BeforeEach
    void setUp() throws Exception {
        support = new InventoryTestSupport(environment, jdbc);
    }

    private static String reserveJson(long orderId, long variantId, int quantity) {
        return "{\"orderId\":" + orderId + ",\"variantId\":" + variantId + ",\"quantity\":" + quantity + "}";
    }

    @Test
    void receiptsAdjustmentsAndDamageUpdateStockAndWriteLedgerRows() throws Exception {
        long variant = support.createStockedVariant(10);
        String base = INVENTORY + "/stock/" + variant;

        Response adjusted = support.admin("POST", base + "/adjustments", "{\"delta\":-3,\"reason\":\"cycle count\"}");
        expect(adjusted, 200, "adjust");
        assertThat(adjusted.body()).contains("\"onHand\":7");
        expect(support.admin("POST", base + "/damage", "{\"quantity\":2,\"reason\":\"water damage\"}"), 200, "damage");

        assertThat(support.onHand(variant)).isEqualTo(5);
        assertThat(support.damaged(variant)).isEqualTo(2);
        List<BigDecimal> balances = jdbc.queryForList(
                "SELECT balance_on_hand_after FROM stock_movements WHERE variant_id = ? ORDER BY id",
                BigDecimal.class, variant);
        assertThat(balances).extracting(BigDecimal::intValue).containsExactly(10, 7, 5);
        Integer withoutActor = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_movements WHERE variant_id = ? AND actor_user_id IS NULL", Integer.class, variant);
        assertThat(withoutActor).isZero();
    }

    @Test
    void anAdjustmentCannotDropOnHandBelowReservedStock() throws Exception {
        long variant = support.createStockedVariant(5);
        expect(support.admin("POST", INVENTORY + "/reservations", reserveJson(System.nanoTime(), variant, 4)), 201, "reserve");

        Response rejected = support.admin("POST", INVENTORY + "/stock/" + variant + "/adjustments",
                "{\"delta\":-2,\"reason\":\"recount\"}");

        assertThat(rejected.status()).isEqualTo(409);
        assertThat(rejected.body()).contains("INSUFFICIENT_STOCK");
        assertThat(support.onHand(variant)).isEqualTo(5);
        assertThat(support.ledgerCount(variant, "ADJUSTMENT")).isZero();
    }

    @Test
    void releasingAnOrderReturnsItsReservedStock() throws Exception {
        long variant = support.createStockedVariant(5);
        long orderId = System.nanoTime();
        expect(support.admin("POST", INVENTORY + "/reservations", reserveJson(orderId, variant, 2)), 201, "reserve");
        assertThat(support.reserved(variant)).isEqualTo(2);

        Response released = support.admin("DELETE", INVENTORY + "/reservations/orders/" + orderId, null);

        expect(released, 200, "release");
        assertThat(released.body()).contains("\"released\":1");
        assertThat(support.reserved(variant)).isZero();
        assertThat(support.reservationCount(variant, "RELEASED")).isEqualTo(1);
        assertThat(support.ledgerCount(variant, "RELEASE")).isEqualTo(1);
    }

    @Test
    void invalidAndUnknownReservationsAreRejectedCleanly() throws Exception {
        long stocked = support.createStockedVariant(1);
        long withoutStockRow = support.createVariantWithoutStockRow();

        Response zero = support.admin("POST", INVENTORY + "/reservations", reserveJson(System.nanoTime(), stocked, 0));
        assertThat(zero.status()).isEqualTo(400);
        assertThat(zero.body()).contains("VALIDATION_FAILED");

        Response missing = support.admin("POST", INVENTORY + "/reservations", reserveJson(System.nanoTime(), withoutStockRow, 1));
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.body()).contains("STOCK_NOT_FOUND");
    }

    @Test
    void permissionsDecideWhoCanViewAndWhoCanChangeStock() throws Exception {
        long variant = support.createStockedVariant(3);
        String designer = support.newStaff("DESIGNER", emails);
        String production = support.newStaff("PRODUCTION_MANAGER", emails);
        String warehouse = support.newStaff("WAREHOUSE_MANAGER", emails);

        assertThat(support.api().get(INVENTORY + "/stock", null).status()).isEqualTo(401);
        assertThat(support.as(designer, "GET", INVENTORY + "/stock", null).status()).isEqualTo(403);

        assertThat(support.as(production, "GET", INVENTORY + "/stock/" + variant, null).status()).isEqualTo(200);
        assertThat(support.as(production, "POST", INVENTORY + "/stock/" + variant + "/adjustments",
                "{\"delta\":1,\"reason\":\"nope\"}").status()).isEqualTo(403);
        assertThat(support.as(production, "GET", INVENTORY + "/audits", null).status()).isEqualTo(403);

        assertThat(support.as(warehouse, "POST", INVENTORY + "/stock/" + variant + "/adjustments",
                "{\"delta\":1,\"reason\":\"found one\"}").status()).isEqualTo(200);
        assertThat(support.as(warehouse, "GET", INVENTORY + "/audits", null).status()).isEqualTo(200);
    }

    @Test
    void completingAnAuditAppliesVariancesAsLedgerAdjustments() throws Exception {
        long variant = support.createStockedVariant(10);
        String audit = field(expect(support.admin("POST", INVENTORY + "/audits", "{\"notes\":\"Q4 count\"}"), 201, "start audit"), "publicId");
        String auditPath = INVENTORY + "/audits/" + audit;

        Response counted = support.admin("POST", auditPath + "/counts/finished-goods",
                "{\"variantId\":" + variant + ",\"countedQuantity\":8}");
        expect(counted, 200, "count");
        assertThat(counted.body()).contains("\"variance\":-2");

        Response completed = support.admin("POST", auditPath + "/complete", "{\"applyAdjustments\":true}");
        expect(completed, 200, "complete");
        assertThat(completed.body()).contains("\"adjustmentsApplied\":1");
        assertThat(support.onHand(variant)).isEqualTo(8);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT reference_type, reference_id FROM stock_movements WHERE variant_id = ? AND movement_type = 'ADJUSTMENT'",
                variant);
        assertThat(row.get("reference_type")).isEqualTo("INVENTORY_AUDIT");
        assertThat(row.get("reference_id")).isEqualTo(audit);
        Integer linked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_audit_lines WHERE variant_id = ? AND adjustment_movement_id IS NOT NULL",
                Integer.class, variant);
        assertThat(linked).isEqualTo(1);

        Response again = support.admin("POST", auditPath + "/complete", "{\"applyAdjustments\":true}");
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.body()).contains("STOCK_CONFLICT");
    }

    @Test
    void stockListShowsVariantDetailsAndSupportsSearch() throws Exception {
        long variant = support.createStockedVariant(4);
        String sku = jdbc.queryForObject("SELECT sku FROM product_variants WHERE id = ?", String.class, variant);

        Response searched = support.admin("GET", INVENTORY + "/stock?q=" + sku, null);

        expect(searched, 200, "search stock");
        assertThat(searched.body()).contains("\"variantId\":" + variant)
                .contains("\"sku\":\"" + sku + "\"").contains("\"productName\":\"Tee ").contains("\"onHand\":4");
        Response single = support.admin("GET", INVENTORY + "/stock/" + variant, null);
        expect(single, 200, "get stock");
        assertThat(single.body()).contains("\"sku\":\"" + sku + "\"");
    }

    @Test
    void variantsWithoutAStockRowCanBeFoundAndInitialisedInBulk() throws Exception {
        long variant = support.createVariantWithoutStockRow();
        String sku = jdbc.queryForObject("SELECT sku FROM product_variants WHERE id = ?", String.class, variant);

        Response pending = support.admin("GET", INVENTORY + "/stock/uninitialized?q=" + sku, null);
        expect(pending, 200, "uninitialised variants");
        assertThat(pending.body()).contains("\"variantId\":" + variant);

        expect(support.admin("POST", INVENTORY + "/stock/initialize-missing", "{\"lowStockThreshold\":3}"),
                200, "initialise missing");

        assertThat(jdbc.queryForObject(
                "SELECT low_stock_threshold FROM finished_goods_stock WHERE variant_id = ?", Integer.class, variant))
                .isEqualTo(3);
        Response after = support.admin("GET", INVENTORY + "/stock/uninitialized?q=" + sku, null);
        assertThat(after.body()).doesNotContain("\"variantId\":" + variant);
    }

    @Test
    void initialisingStockForAnUnknownVariantIsA404() throws Exception {
        Response missing = support.admin("POST", INVENTORY + "/stock", "{\"variantId\":999999999,\"lowStockThreshold\":1}");

        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.body()).contains("VARIANT_NOT_FOUND");
    }

    @Test
    void creatingAVariantInitialisesItsStockRowAutomatically() throws Exception {
        long variant = support.createVariant();

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM finished_goods_stock WHERE variant_id = ?",
                Integer.class,
                variant);

        assertThat(rows).isEqualTo(1);
        assertThat(support.onHand(variant)).isZero();
    }


    @Test
    void staffActionsAreWrittenToTheAuditLog() throws Exception {
        long variant = support.createStockedVariant(10);

        expect(
                support.admin(
                        "POST",
                        INVENTORY + "/stock/" + variant + "/adjustments",
                        "{\"delta\":-1,\"reason\":\"audit log check\"}"
                ),
                200,
                "adjust"
        );

        Response started = expect(
                support.admin(
                        "POST",
                        INVENTORY + "/audits",
                        "{\"notes\":\"log check\"}"
                ),
                201,
                "start audit"
        );

        String auditNumber = field(started, "auditNumber");
        String auditId = field(started, "publicId");

        expect(
                support.admin(
                        "POST",
                        INVENTORY + "/audits/" + auditId + "/counts/finished-goods",
                        "{\"variantId\":" + variant + ",\"countedQuantity\":9}"
                ),
                200,
                "count"
        );

        expect(
                support.admin(
                        "POST",
                        INVENTORY + "/audits/" + auditId + "/complete",
                        "{\"applyAdjustments\":false}"
                ),
                200,
                "complete"
        );

        assertThat(auditLogCount("STOCK_RECEIVED", String.valueOf(variant)))
                .isEqualTo(1);

        assertThat(auditLogCount("STOCK_ADJUSTED", String.valueOf(variant)))
                .isEqualTo(1);

        assertThat(auditLogCount("INVENTORY_AUDIT_STARTED", auditNumber))
                .isEqualTo(1);

        assertThat(auditLogCount("INVENTORY_AUDIT_COMPLETED", auditNumber))
                .isEqualTo(1);

        Integer withoutActor = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs "
                        + "WHERE action = 'STOCK_ADJUSTED' "
                        + "AND entity_id = ? "
                        + "AND actor_user_id IS NULL",
                Integer.class,
                String.valueOf(variant)
        );

        assertThat(withoutActor).isZero();
    }

    private int auditLogCount(String action, String entityId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = ? AND entity_id = ?",
                Integer.class,
                action,
                entityId
        );
    }

}
