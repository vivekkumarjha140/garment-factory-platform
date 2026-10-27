# ==============================================================================
# GARMENT PRINTING FACTORY PLATFORM — MASTER PROGRESS HANDOFF (Phases 0 to 5)
# ==============================================================================

## 1. PROJECT STATE AT A GLANCE
Phases 0 through 5 are FULLY IMPLEMENTED, COMPILED, TESTED, AND VERIFIED.
- Backend (Java 21 / Spring Boot 4.1.1): 109 unit & ArchUnit tests PASS (0 failures, 0 errors).
- Frontend (React 19 / TypeScript / Vite 7 / Tailwind 4): 26 test files, 193 Vitest tests PASS.
- Frontend Production Build: `npm run build` completes cleanly with 0 TypeScript/Rollup errors.
- Database: MySQL 8.4 LTS with 29 Flyway migrations (V001 to V029).
- Next Immediate Step: Start PHASE 6 (Shopping Cart & Checkout Preparation).

---

## 2. PROJECT METADATA & REPOSITORY PATHS
- **Root Directory**: `D:\AJDA\garment-factory-platform`
- **Backend Directory**: `D:\AJDA\garment-factory-platform\backend`
- **Frontend Directory**: `D:\AJDA\garment-factory-platform\frontend`
- **Infrastructure**: `D:\AJDA\garment-factory-platform\infra`
- **OS & Tools**: Windows 11, PowerShell, IntelliJ IDEA
- **Architecture**: Modular Monolith. Base package: `com.garmentfactory`
- **Module Structure**: `api/`, `controller/`, `dto/`, `entity/`, `mapper/`, `repository/`, `service/`
- **ArchUnit Guards**:
  1. Controllers never touch repositories directly (services only).
  2. Shared module never depends on business modules.
  3. No cyclic dependencies between top-level modules.
- **Error Handling**: RFC 9457 `ProblemDetail` with stable error `code`. Business exceptions extend `ApiException(HttpStatus, code, message)`.
- **Authentication**: JWT HS256 access token (in memory) + opaque refresh token in HttpOnly, SameSite=Strict cookie scoped to `/api/v1/auth`. Requires `X-Requested-With: XMLHttpRequest` header and allowed Origin.
- **Database Engine**: MySQL 8.4 LTS (InnoDB, `utf8mb4_0900_ai_ci`). `ddl-auto: validate`. Timestamps are `Instant` on `DATETIME(6) UTC`.

---

## 3. COMPLETED PHASES BREAKDOWN

| Phase / Step | Name & Scope | Status | Verification Evidence |
| :--- | :--- | :--- | :--- |
| **Phase 0** | Requirements, workflows (Normal, Custom, Bulk), 18 module boundaries, Master Feature Checklist. | ✅ Delivered | Architecture signed off |
| **Phase 1** | System design, 78-table relational schema, constraints, ER diagram, concurrency rules. | ✅ Delivered | DB conventions established |
| **Phase 2** | Project skeleton, Docker Compose (MySQL, Redis, MinIO, Mailpit), Flyway pipeline. | ✅ Delivered | Services running & healthy |
| **Phase 3.1** | Core auth: registration, email verification, login, JWT issuance, refresh rotation, lockout, admin bootstrap. | ✅ Delivered | Unit & IT tests pass |
| **Phase 3.2** | Redis rate limiting (fail-open), client IP resolver, audit module, sessions, change password. | ✅ Delivered | RedisRateLimiter tests pass |
| **Phase 3.3** | Admin user/role/permission APIs, audit-log read API. | ✅ Delivered | RBAC tests pass |
| **Phase 3.4** | Frontend auth: login, register, verify, forgot/reset password, sessions panel, admin users table, route guards. | ✅ Delivered | Vitest auth suites pass |
| **Phase 4.1** | Customer profile (`/customers/me`) and saved address book with Indian PIN code rules. | ✅ Delivered | `AddressServiceTest` pass |
| **Phase 4.2** | Catalog model & staff APIs: categories tree, brands, products, variants, SKUs, quantity price tiers. | ✅ Delivered | `CatalogSortTest`, `SlugsTest` pass |
| **Phase 4.3** | Public shop API: search, filters, sorting, paging, facets, image upload to local/MinIO storage. | ✅ Delivered | `ImageValidatorTest` pass |
| **Phase 4.4** | Frontend Shop & Customer Account: Shop catalog (`/shop`), filters, variant picker, image gallery, address CRUD. | ✅ Delivered | Merged & tested (100% clean) |
| **Phase 5** | Inventory Management: finished goods stock, raw materials, immutable ledger, atomic reservations, sweeper, audits. | ✅ Delivered | 109 Backend tests & 193 Frontend tests pass |

---

## 4. COMPLETE DATABASE MIGRATIONS (V001 TO V029)

```text
V001__create_app_settings.sql
V002__create_number_sequences.sql
V003__seed_app_settings.sql
V004__create_users.sql
V005__create_roles.sql
V006__create_permissions.sql
V007__create_role_permissions.sql
V008__create_user_roles.sql
V009__create_refresh_tokens.sql
V010__create_verification_tokens.sql
V011__seed_roles_and_permissions.sql
V012__create_audit_logs.sql
V013__create_customer_profiles.sql
V014__create_addresses.sql (stored generated columns for single default address)
V015__create_catalog_permissions.sql
V016__create_categories.sql
V017__create_brands.sql
V018__create_products.sql
V019__create_product_variants.sql (unique SKU, unique product+size+color)
V020__create_product_images.sql (stored generated columns for single primary image)
V021__create_quantity_price_tiers.sql
V022__create_finished_goods_stock.sql (CHECK on_hand >= 0, reserved <= on_hand, version)
V023__create_raw_materials.sql (FABRIC, BLANK_GARMENT, CONSUMABLE, PACKAGING, DECIMAL(14,3))
V024__create_stock_receipts.sql
V025__create_stock_reservations.sql (ACTIVE, CONSUMED, RELEASED, EXPIRED, idx status+expires_at)
V026__create_stock_movements.sql (ledger: RECEIPT, RESERVATION, DEDUCTION, RELEASE, ADJUSTMENT, DAMAGE)
V027__create_inventory_audits.sql (+ inventory_audit_lines)
V028__protect_stock_movements_immutable.sql (DB triggers blocking UPDATE & DELETE on ledger)
V029__create_inventory_permissions.sql (STOCK_VIEW, STOCK_ADJUST, INVENTORY_AUDIT)
```

---

## 5. ROLES & PERMISSIONS MATRIX

### Active Roles (11):
`CUSTOMER`, `ADMIN`, `FACTORY_MANAGER`, `PRODUCTION_MANAGER`, `DESIGNER`, `WAREHOUSE_MANAGER`, `SALES_MANAGER`, `FINANCE_MANAGER`, `DISPATCH_MANAGER`, `QC_INSPECTOR`, `PRODUCTION_OPERATOR`.

### Active Permissions:
- **Identity**: `USER_VIEW`, `USER_MANAGE`, `USER_BLOCK`, `ROLE_MANAGE`
- **Audit**: `AUDIT_VIEW`
- **Catalog**: `PRODUCT_VIEW`, `PRODUCT_WRITE`, `CATEGORY_WRITE`
- **Inventory**: `STOCK_VIEW`, `STOCK_ADJUST`, `INVENTORY_AUDIT`

### Role-to-Permission Mapping:
- **ADMIN**: Holds ALL permissions.
- **WAREHOUSE_MANAGER**: `STOCK_VIEW`, `STOCK_ADJUST`, `INVENTORY_AUDIT`, `PRODUCT_VIEW`.
- **FACTORY_MANAGER**: `STOCK_VIEW`, `STOCK_ADJUST`, `PRODUCT_VIEW`, `PRODUCT_WRITE`, `CATEGORY_WRITE`, `AUDIT_VIEW`.
- **PRODUCTION_MANAGER / SALES_MANAGER / FINANCE_MANAGER / DISPATCH_MANAGER**: `STOCK_VIEW`, `PRODUCT_VIEW`.
- **CUSTOMER / DESIGNER / OPERATOR**: No stock management access (enforced via backend `@PreAuthorize`).

---

## 6. COMPLETE API INVENTORY (ALL UNDER `/api/v1`)

### A. Public Authentication & System
- `POST /auth/register`, `/auth/verify-email`, `/auth/resend-verification`
- `POST /auth/login`, `/auth/refresh` (cookie + custom header), `/auth/logout`
- `POST /auth/forgot-password`, `/auth/reset-password`
- `GET /system/ping`, `/actuator/health`, `/actuator/health/readiness`

### B. Customer Signed-in
- `GET /auth/sessions`, `DELETE /auth/sessions/{id}`, `POST /auth/logout-all`, `POST /auth/change-password`
- `GET /users/me`, `GET /customers/me`, `PATCH /customers/me`
- `GET /customers/me/addresses`, `POST /customers/me/addresses`
- `PUT /customers/me/addresses/{id}`, `DELETE /customers/me/addresses/{id}`
- `POST /customers/me/addresses/{id}/default-shipping`, `POST /customers/me/addresses/{id}/default-billing`
- `GET /customers/reference/states` (List of 36 Indian states & UTs)

### C. Public Shop (No Sign-in Needed)
- `GET /catalog/categories`, `GET /catalog/brands`
- `GET /catalog/products` (Params: `q`, `category`, `brand`, `minPrice`, `maxPrice`, `sizes` [repeated], `colors` [repeated], `customizable`, `sort`, `page`, `size`)
- `GET /catalog/products/{slug}`, `GET /catalog/facets?category={slug}`, `GET /catalog/images/{id}`

### D. Admin Staff Catalog
- `/admin/catalog/categories`, `/brands` (GET, POST, PUT)
- `/admin/catalog/products` (GET list, GET `{id}`, POST, PUT, POST `{id}/status`)
- `/admin/catalog/products/{id}/variants` (POST, PUT, POST status)
- `/admin/catalog/products/{id}/price-tiers` (GET, PUT)
- `/admin/catalog/products/{id}/images` (GET, POST multipart, PUT, DELETE)

### E. Inventory & Stock (Staff Only)
- **STOCK_VIEW**:
  - `GET /inventory/stock?q=&lowStockOnly=&page=&size=`
  - `GET /inventory/stock/{variantId}`, `GET /inventory/stock/{variantId}/movements`
  - `GET /inventory/receipts`
  - `GET /inventory/raw-materials?category=&lowStockOnly=`, `GET /inventory/raw-materials/{id}`, `GET /inventory/raw-materials/{id}/movements`
- **STOCK_ADJUST**:
  - `POST /inventory/stock` (initialize stock row), `POST /inventory/stock/initialize-missing`
  - `GET /inventory/stock/uninitialized?q=&limit=`
  - `PATCH /inventory/stock/{variantId}/threshold`
  - `POST /inventory/stock/{variantId}/receipts`, `/adjustments`, `/damage`
  - `POST /inventory/reservations` (orderId, variantId, qty), `DELETE /inventory/reservations/orders/{orderId}`
  - `POST /inventory/raw-materials`, `POST /inventory/raw-materials/{id}/receipts`, `POST /inventory/raw-materials/{id}/adjustments`
- **INVENTORY_AUDIT**:
  - `POST /inventory/audits`, `GET /inventory/audits?status=`, `GET /inventory/audits/{publicId}`
  - `POST /inventory/audits/{publicId}/counts/finished-goods`, `/counts/raw-materials`
  - `POST /inventory/audits/{publicId}/complete` (with `applyAdjustments: true|false`), `POST /inventory/audits/{publicId}/cancel`

---

## 7. CORE ARCHITECTURE & DATA INTEGRITY INVARIANTS

1. **Atomic Concurrency Formula**:
   - `available = on_hand - reserved`
   - Atomic reservation update:
     ```sql
     UPDATE finished_goods_stock
        SET reserved = reserved + :qty, version = version + 1
      WHERE variant_id = :variantId AND (on_hand - reserved) >= :qty;
     ```
   - Affected rows = 1: Success. Affected rows = 0: Throws `InsufficientStockException` (409 INSUFFICIENT_STOCK).
2. **Immutable Stock Ledger**:
   - `stock_movements` table is insert-only at the application level (`@Immutable`, `updatable = false` on all columns) and guarded at the DB level by `V028` triggers.
3. **Reservation Sweeper**:
   - Background `@Scheduled` job selects expired reservations with `status = 'ACTIVE'` and `expires_at < UTC_NOW()` using `SELECT ... FOR UPDATE SKIP LOCKED` to release stock without blocking new incoming orders.
4. **Single-Flight Token Refresh**:
   - Frontend Axios interceptor uses browser Web Locks / in-flight promise sharing so that 5 simultaneous expired API calls trigger exactly ONE refresh request.
5. **No Soft Deletes on Critical Records**:
   - Users, Products, Variants, Stock, Orders, Invoices, Payments, and Audit Logs are NEVER deleted; status flags (`ARCHIVED`, `BLOCKED`, `CANCELLED`) are used.
6. **India-First Localization**:
   - Currency is INR (`DECIMAL(12,2)`).
   - Validations enforce Indian 6-digit PIN codes (`^[1-9][0-9]{5}$`) and standard 10–15 digit phone numbers.
   - 36 States/UTs reference data seeded.

---

## 8. VERIFIED COMMANDS & EXECUTION EVIDENCE

### Environment Configuration:
Fill `.env` from `.env.example`:
```bash
docker compose --env-file .env -f infra/docker-compose.yml up -d mysql redis minio mailpit
```

### Backend Commands (run from root):
```powershell
# Compile backend:
.\mvnw.cmd -f backend\pom.xml compile -DskipTests
# Run 109 Unit & ArchUnit tests:
.\mvnw.cmd -f backend\pom.xml test
# Run application:
.\mvnw.cmd -f backend\pom.xml spring-boot:run
```
*Result: 109 tests passed, 0 failures, 0 errors.*

### Frontend Commands (run from `frontend/` directory):
```powershell
cd frontend
npm install
npm run typecheck    # 0 errors
npm test             # 26 suites, 193 tests passed!
npm run build        # dist/ created in ~20s
```
*Result: 193 tests passed, production bundle created.*

---

## 9. DEFERRED ITEMS ROADMAP (PHASES 6 TO 15)

- **Phase 6**: Shopping Cart & Checkout Preparation (DB-backed persistent cart, server-side re-pricing, coupons engine, shipping zones calculation).
- **Phase 7**: Normal Orders & Razorpay Sandbox Payments (Order snapshot, idempotency, webhook HMAC verification, PDF tax invoice, tracking).
- **Phase 8**: Custom Garment Printing Workflow (Magic-byte file uploads to MinIO/S3, signed URLs, designer review/approval queue, print pricing rules).
- **Phase 9**: Bulk & B2B Orders (Company accounts, bulk inquiries, quotation revisions, price freezing, advance & balance payments).
- **Phase 10**: Factory Production & QC (BOM calculation, configurable stages, operator tasks, QC inspections with defect logs, rework/scrap routing).
- **Phase 11**: Shipping, Courier & Returns (Shiprocket sandbox/manual dispatch, tracking events, configurable return window, return inspection).
- **Phase 12**: Admin Dashboards, Real-Time SSE & Reports (Executive analytics, Server-Sent Events notification stream, audit log viewer).
- **Phase 13**: Full-Stack UI Polish & Responsiveness (Accessibility WCAG AA, skeletons, mobile layout).
- **Phase 14**: Security Hardening & Concurrency Stress Tests (Multi-threaded stock purchase tests, penetration audit).
- **Phase 15**: Production Deployment & DevOps Runbooks (Multi-stage Docker, Nginx TLS proxy, MySQL backup/restore scripts).

```
