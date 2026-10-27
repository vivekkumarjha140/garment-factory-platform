INSERT INTO permissions (code, description) VALUES
                                                ('ORDER_VIEW',   'View all customer orders and download their invoices'),
                                                ('ORDER_FULFIL', 'Move orders through processing, packing, shipping and delivery, and collect cash on delivery'),
                                                ('ORDER_CANCEL', 'Cancel customer orders on their behalf; paid orders are refunded');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('ORDER_VIEW', 'ORDER_FULFIL', 'ORDER_CANCEL')
WHERE r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('ORDER_VIEW', 'ORDER_FULFIL')
WHERE r.code IN ('FACTORY_MANAGER', 'DISPATCH_MANAGER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('ORDER_VIEW', 'ORDER_CANCEL')
WHERE r.code IN ('SALES_MANAGER', 'FINANCE_MANAGER');