package com.garmentfactory.inventory;

import static com.garmentfactory.inventory.InventoryTestSupport.INVENTORY;
import static com.garmentfactory.inventory.InventoryTestSupport.expect;
import static com.garmentfactory.inventory.InventoryTestSupport.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataAccessException;
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
        long withoutStockRow = support.createVariant();

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
    void theLedgerIsImmutableAtDatabaseLevel() throws Exception {
        long variant = support.createStockedVariant(1);

        assertThatThrownBy(() -> jdbc.update("UPDATE stock_movements SET reason = 'tamper' WHERE variant_id = ?", variant))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM stock_movements WHERE variant_id = ?", variant))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThat(support.ledgerCount(variant, "RECEIPT")).isEqualTo(1);
    }

    @Test
    void completingAnAuditAppliesVariancesAsLedgerAdjustments() throws Exception {
        long variant = support.createStockedVariant(10);
        String audit = field(expect(support.admin("POST", INVENTORY + "/audits", "{\"notes\":\"Q4 count\"}"), 201, "start audit"), "publicId");
        String auditPath = INVENTORY + "/audits/" + audit;

        Response counted = support.admin("POST", auditPath + "/counts/finished-goods",
                "{\"variantId\":" + variant + ",\"countedQuantity\":8}");
        expect(counted, 201, "count");
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
}