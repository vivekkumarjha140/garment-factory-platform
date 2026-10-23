INSERT INTO permissions (code, description) VALUES
                                                ('STOCK_VIEW',      'View stock levels, stock movements and receipts'),
                                                ('STOCK_ADJUST',    'Receive stock, make manual adjustments and mark damaged stock'),
                                                ('INVENTORY_AUDIT', 'Run, review and complete inventory audits');

-- Full inventory control
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('STOCK_VIEW', 'STOCK_ADJUST', 'INVENTORY_AUDIT')
WHERE r.code IN ('ADMIN', 'WAREHOUSE_MANAGER');

-- Factory manager: can see stock and receive/adjust it
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('STOCK_VIEW', 'STOCK_ADJUST')
WHERE r.code = 'FACTORY_MANAGER';

-- Read-only visibility
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'STOCK_VIEW'
WHERE r.code IN ('PRODUCTION_MANAGER', 'SALES_MANAGER', 'FINANCE_MANAGER', 'DISPATCH_MANAGER');