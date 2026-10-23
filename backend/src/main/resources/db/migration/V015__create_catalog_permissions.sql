INSERT INTO permissions (code, description) VALUES
                                                ('PRODUCT_VIEW',   'View every product, including drafts and inactive ones'),
                                                ('PRODUCT_WRITE',  'Create and edit products, variants and prices'),
                                                ('CATEGORY_WRITE', 'Create and edit categories and brands');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('PRODUCT_VIEW', 'PRODUCT_WRITE', 'CATEGORY_WRITE')
WHERE r.code IN ('ADMIN', 'FACTORY_MANAGER', 'SALES_MANAGER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('PRODUCT_VIEW', 'PRODUCT_WRITE')
WHERE r.code = 'WAREHOUSE_MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'PRODUCT_VIEW'
WHERE r.code IN ('PRODUCTION_MANAGER', 'DESIGNER', 'FINANCE_MANAGER', 'DISPATCH_MANAGER');