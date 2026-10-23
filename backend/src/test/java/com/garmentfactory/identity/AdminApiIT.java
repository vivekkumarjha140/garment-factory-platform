package com.garmentfactory.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 * Role boundaries and administration rules against a real MySQL 8.4, with a bootstrapped administrator.
 * Tests that change role permissions use a role no other test depends on. Requires Docker.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin"})
class AdminApiIT {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "Adm1nStrongPass";
    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final Pattern PUBLIC_ID = Pattern.compile("\"publicId\":\"([^\"]+)\"");

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
    private String adminToken;

    private record Account(String email, String publicId, String accessToken, String refreshToken) {
    }

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(environment);
        Response login = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(login.status()).as("bootstrap admin login: %s", login.body()).isEqualTo(200);
        adminToken = login.accessToken();
    }

    private Response login(String email, String password) throws Exception {
        return api.post("/api/v1/auth/login", ApiClient.loginJson(email, password));
    }

    private Response as(String token, String method, String path, String body) throws Exception {
        return api.send(method, path, body, Map.of("Authorization", "Bearer " + token));
    }

    private static String publicIdOf(String body) {
        Matcher matcher = PUBLIC_ID.matcher(body);
        assertThat(matcher.find()).as("publicId in %s", body).isTrue();
        return matcher.group(1);
    }

    private static String rolesJson(String... roles) {
        return Arrays.stream(roles).map(role -> "\"" + role + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private int auditCount(String action, String entityId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = ? AND entity_id = ?",
                Integer.class, action, entityId);
    }

    /** Admin invites a staff member, who sets a password through the emailed link and signs in. */
    private Account newStaff(String... roles) throws Exception {
        String email = "staff-" + UUID.randomUUID() + "@example.com";
        Response created = as(adminToken, "POST", "/api/v1/admin/users",
                "{\"fullName\":\"Staff Member\",\"email\":\"" + email + "\",\"roles\":" + rolesJson(roles) + "}");
        assertThat(created.status()).as("create staff: %s", created.body()).isEqualTo(201);
        String token = emails.latestTokenTo(email);
        assertThat(api.post("/api/v1/auth/reset-password",
                "{\"token\":\"" + token + "\",\"newPassword\":\"" + PASSWORD + "\"}").status()).isEqualTo(204);
        Response login = login(email, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        return new Account(email, publicIdOf(created.body()), login.accessToken(), login.refreshToken());
    }

    private Account newCustomer() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        assertThat(api.post("/api/v1/auth/register", ApiClient.registerJson(email, PASSWORD)).status()).isEqualTo(202);
        api.post("/api/v1/auth/verify-email", "{\"token\":\"" + emails.latestTokenTo(email) + "\"}");
        Response login = login(email, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        return new Account(email, publicIdOf(login.body()), login.accessToken(), login.refreshToken());
    }

    @Test
    void anonymousGets401AndCustomersGet403EverywhereUnderAdmin() throws Exception {
        assertThat(api.get("/api/v1/admin/users", null).status()).isEqualTo(401);

        Account customer = newCustomer();
        List<String[]> calls = List.of(
                new String[]{"GET", "/api/v1/admin/users"},
                new String[]{"GET", "/api/v1/admin/roles"},
                new String[]{"GET", "/api/v1/admin/permissions"},
                new String[]{"GET", "/api/v1/admin/audit-logs"},
                new String[]{"POST", "/api/v1/admin/users"});
        for (String[] call : calls) {
            String body = call[0].equals("POST")
                    ? "{\"fullName\":\"Test User\",\"email\":\"test-user@example.com\",\"roles\":[\"DESIGNER\"]}"
                    : null;

            Response response = as(customer.accessToken(), call[0], call[1], body);
            assertThat(response.status())
                    .as("%s %s", call[0], call[1])
                    .isEqualTo(403);
        }
    }

    @Test
    void factoryManagerCanReadButNotChange() throws Exception {
        Account manager = newStaff("FACTORY_MANAGER");
        String token = manager.accessToken();

        assertThat(as(token, "GET", "/api/v1/admin/users", null).status()).isEqualTo(200);
        assertThat(as(token, "GET", "/api/v1/admin/roles", null).status()).isEqualTo(200);
        assertThat(as(token, "GET", "/api/v1/admin/audit-logs", null).status()).isEqualTo(200);

        assertThat(as(token, "GET", "/api/v1/admin/permissions", null).status()).isEqualTo(403);
        assertThat(as(token, "POST", "/api/v1/admin/users",
                "{\"fullName\":\"X\",\"email\":\"x@example.com\",\"roles\":[\"DESIGNER\"]}").status()).isEqualTo(403);
        assertThat(as(token, "PUT", "/api/v1/admin/users/" + manager.publicId() + "/roles",
                "{\"roles\":[\"DESIGNER\"]}").status()).isEqualTo(403);
        assertThat(as(token, "POST", "/api/v1/admin/users/" + manager.publicId() + "/block", "{}").status()).isEqualTo(403);
        assertThat(as(token, "PUT", "/api/v1/admin/roles/DESIGNER/permissions", "{\"permissions\":[]}").status()).isEqualTo(403);
    }

    @Test
    void adminCanSearchFilterAndPageUsers() throws Exception {
        Account staff = newStaff("FACTORY_MANAGER");
        String fragment = staff.email().substring(0, staff.email().indexOf('@'));

        Response byText = as(adminToken, "GET", "/api/v1/admin/users?q=" + fragment, null);
        assertThat(byText.status()).isEqualTo(200);
        assertThat(byText.body()).contains(staff.email()).contains("\"totalElements\":1");

        Response byRoleAndStatus = as(adminToken, "GET",
                "/api/v1/admin/users?q=" + fragment + "&role=FACTORY_MANAGER&status=ACTIVE", null);
        assertThat(byRoleAndStatus.body()).contains(staff.email());

        Response wrongRole = as(adminToken, "GET", "/api/v1/admin/users?q=" + fragment + "&role=DESIGNER", null);
        assertThat(wrongRole.body()).contains("\"totalElements\":0");

        assertThat(as(adminToken, "GET", "/api/v1/admin/users?size=1000", null).body()).contains("\"size\":100");
        Response badStatus = as(adminToken, "GET", "/api/v1/admin/users?status=NOPE", null);
        assertThat(badStatus.status()).isEqualTo(400);
        assertThat(badStatus.body()).contains("INVALID_STATUS");
    }

    @Test
    void invitedStaffActivateByChoosingAPassword() throws Exception {
        String email = "invitee-" + UUID.randomUUID() + "@example.com";

        Response created = as(adminToken, "POST", "/api/v1/admin/users",
                "{\"fullName\":\"New Designer\",\"email\":\"" + email + "\",\"roles\":[\"DESIGNER\"]}");
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body()).contains("PENDING_VERIFICATION").contains("DESIGNER");
        String publicId = publicIdOf(created.body());

        assertThat(login(email, PASSWORD).status()).isEqualTo(401);

        String token = emails.latestTokenTo(email);
        String resetBody = "{\"token\":\"" + token + "\",\"newPassword\":\"" + PASSWORD + "\"}";
        assertThat(api.post("/api/v1/auth/reset-password", resetBody).status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE email = ?", String.class, email)).isEqualTo("ACTIVE");

        Response login = login(email, PASSWORD);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body()).contains("DESIGNER");
        assertThat(api.post("/api/v1/auth/reset-password", resetBody).status()).isEqualTo(400);
        assertThat(auditCount("STAFF_USER_CREATED", publicId)).isEqualTo(1);
    }

    @Test
    void duplicateEmailUnknownRoleAndEmptyRolesAreRejected() throws Exception {
        Account existing = newStaff("DESIGNER");

        Response duplicate = as(adminToken, "POST", "/api/v1/admin/users",
                "{\"fullName\":\"Dup\",\"email\":\"" + existing.email() + "\",\"roles\":[\"DESIGNER\"]}");
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body()).contains("EMAIL_ALREADY_EXISTS");

        Response unknownRole = as(adminToken, "POST", "/api/v1/admin/users",
                "{\"fullName\":\"X\",\"email\":\"new-" + UUID.randomUUID() + "@example.com\",\"roles\":[\"NOT_A_ROLE\"]}");
        assertThat(unknownRole.status()).isEqualTo(400);
        assertThat(unknownRole.body()).contains("UNKNOWN_ROLE");

        Response noRoles = as(adminToken, "POST", "/api/v1/admin/users",
                "{\"fullName\":\"X\",\"email\":\"new-" + UUID.randomUUID() + "@example.com\",\"roles\":[]}");
        assertThat(noRoles.status()).isEqualTo(400);
        assertThat(noRoles.body()).contains("VALIDATION_FAILED");
    }

    @Test
    void roleChangesTakeEffectOnTheNextRequest() throws Exception {
        Account staff = newStaff("FACTORY_MANAGER");
        assertThat(as(staff.accessToken(), "GET", "/api/v1/admin/users", null).status()).isEqualTo(200);

        Response change = as(adminToken, "PUT", "/api/v1/admin/users/" + staff.publicId() + "/roles",
                "{\"roles\":[\"DESIGNER\"]}");

        assertThat(change.status()).isEqualTo(200);
        assertThat(change.body()).contains("DESIGNER");
        assertThat(as(staff.accessToken(), "GET", "/api/v1/admin/users", null).status()).isEqualTo(403);
        assertThat(auditCount("USER_ROLES_CHANGED", staff.publicId())).isEqualTo(1);
    }

    @Test
    void blockingEndsAccessImmediatelyAndUnblockingRestoresIt() throws Exception {
        Account customer = newCustomer();
        assertThat(as(customer.accessToken(), "GET", "/api/v1/users/me", null).status()).isEqualTo(200);

        Response block = as(adminToken, "POST", "/api/v1/admin/users/" + customer.publicId() + "/block",
                "{\"reason\":\"fraud suspicion\"}");
        assertThat(block.status()).isEqualTo(200);
        assertThat(block.body()).contains("BLOCKED");

        assertThat(as(customer.accessToken(), "GET", "/api/v1/users/me", null).status()).isEqualTo(401);
        assertThat(api.postAsBrowser("/api/v1/auth/refresh", customer.refreshToken()).status()).isEqualTo(401);
        Response blockedLogin = login(customer.email(), PASSWORD);
        assertThat(blockedLogin.status()).isEqualTo(403);
        assertThat(blockedLogin.body()).contains("ACCOUNT_BLOCKED");

        Response unblock = as(adminToken, "POST", "/api/v1/admin/users/" + customer.publicId() + "/unblock", null);
        assertThat(unblock.status()).isEqualTo(200);
        assertThat(unblock.body()).contains("ACTIVE");
        assertThat(login(customer.email(), PASSWORD).status()).isEqualTo(200);

        assertThat(auditCount("USER_BLOCKED", customer.publicId())).isEqualTo(1);
        assertThat(auditCount("USER_UNBLOCKED", customer.publicId())).isEqualTo(1);
    }

    @Test
    void anAdminCannotChangeTheirOwnAccount() throws Exception {
        String adminPublicId = jdbc.queryForObject("SELECT public_id FROM users WHERE email = ?", String.class, ADMIN_EMAIL);

        Response block = as(adminToken, "POST", "/api/v1/admin/users/" + adminPublicId + "/block", "{}");
        Response roles = as(adminToken, "PUT", "/api/v1/admin/users/" + adminPublicId + "/roles", "{\"roles\":[\"DESIGNER\"]}");

        assertThat(block.status()).isEqualTo(409);
        assertThat(block.body()).contains("CANNOT_MODIFY_SELF");
        assertThat(roles.status()).isEqualTo(409);
        assertThat(roles.body()).contains("CANNOT_MODIFY_SELF");
    }

    @Test
    void onlyAdministratorsCanTouchTheAdminRole() throws Exception {
        // Delegate user management to SALES_MANAGER (a role no other test depends on).
        assertThat(as(adminToken, "PUT", "/api/v1/admin/roles/SALES_MANAGER/permissions",
                "{\"permissions\":[\"USER_VIEW\",\"USER_MANAGE\"]}").status()).isEqualTo(200);
        Account delegate = newStaff("SALES_MANAGER");

        Response grantAdmin = as(delegate.accessToken(), "POST", "/api/v1/admin/users",
                "{\"fullName\":\"Sneaky\",\"email\":\"sneaky-" + UUID.randomUUID() + "@example.com\",\"roles\":[\"ADMIN\"]}");
        assertThat(grantAdmin.status()).isEqualTo(403);
        assertThat(grantAdmin.body()).contains("ADMIN_ROLE_REQUIRED");

        Account target = newStaff("DESIGNER");
        assertThat(as(delegate.accessToken(), "PUT", "/api/v1/admin/users/" + target.publicId() + "/roles",
                "{\"roles\":[\"ADMIN\"]}").status()).isEqualTo(403);

        Response allowed = as(delegate.accessToken(), "POST", "/api/v1/admin/users",
                "{\"fullName\":\"Fine\",\"email\":\"fine-" + UUID.randomUUID() + "@example.com\",\"roles\":[\"DESIGNER\"]}");
        assertThat(allowed.status()).isEqualTo(201);
    }

    @Test
    void permissionEditingRules() throws Exception {
        Response adminRole = as(adminToken, "PUT", "/api/v1/admin/roles/ADMIN/permissions", "{\"permissions\":[]}");
        assertThat(adminRole.status()).isEqualTo(409);
        assertThat(adminRole.body()).contains("ROLE_IMMUTABLE");

        Response customerRole = as(adminToken, "PUT", "/api/v1/admin/roles/CUSTOMER/permissions",
                "{\"permissions\":[\"USER_VIEW\"]}");
        assertThat(customerRole.status()).isEqualTo(409);

        Response unknown = as(adminToken, "PUT", "/api/v1/admin/roles/DISPATCH_MANAGER/permissions",
                "{\"permissions\":[\"DOES_NOT_EXIST\"]}");
        assertThat(unknown.status()).isEqualTo(400);
        assertThat(unknown.body()).contains("UNKNOWN_PERMISSION");

        // A non-admin with ROLE_MANAGE can only hand out permissions they hold.
        assertThat(as(adminToken, "PUT", "/api/v1/admin/roles/DISPATCH_MANAGER/permissions",
                "{\"permissions\":[\"ROLE_MANAGE\"]}").status()).isEqualTo(200);
        Account dispatcher = newStaff("DISPATCH_MANAGER");

        Response escalation = as(dispatcher.accessToken(), "PUT", "/api/v1/admin/roles/WAREHOUSE_MANAGER/permissions",
                "{\"permissions\":[\"USER_MANAGE\"]}");
        assertThat(escalation.status()).isEqualTo(403);
        assertThat(escalation.body()).contains("PERMISSION_ESCALATION");

        Response heldPermission = as(dispatcher.accessToken(), "PUT", "/api/v1/admin/roles/WAREHOUSE_MANAGER/permissions",
                "{\"permissions\":[\"ROLE_MANAGE\"]}");
        assertThat(heldPermission.status()).isEqualTo(200);
        assertThat(auditCount("ROLE_PERMISSIONS_CHANGED", "WAREHOUSE_MANAGER")).isEqualTo(1);
    }

    @Test
    void auditLogCanBeFilteredAndIsPaged() throws Exception {
        newStaff("DESIGNER");

        Response byAction = as(adminToken, "GET", "/api/v1/admin/audit-logs?action=STAFF_USER_CREATED&size=1000", null);

        assertThat(byAction.status()).isEqualTo(200);
        assertThat(byAction.body()).contains("\"action\":\"STAFF_USER_CREATED\"").contains("\"size\":100");
        Response byType = as(adminToken, "GET", "/api/v1/admin/audit-logs?entityType=ROLE", null);
        assertThat(byType.status()).isEqualTo(200);
        assertThat(byType.body()).doesNotContain("\"entityType\":\"USER\"");
    }
}