INSERT INTO roles (code, name, description, is_system) VALUES
                                                           ('CUSTOMER',            'Customer',             'Retail and business customers', 1),
                                                           ('ADMIN',               'Administrator',        'Full system administration', 1),
                                                           ('FACTORY_MANAGER',     'Factory Manager',      'Overall factory oversight and approvals', 1),
                                                           ('PRODUCTION_MANAGER',  'Production Manager',   'Production planning and work assignment', 1),
                                                           ('DESIGNER',            'Designer',             'Custom design review and approval', 1),
                                                           ('WAREHOUSE_MANAGER',   'Warehouse Manager',    'Raw material and finished goods stock', 1),
                                                           ('SALES_MANAGER',       'Sales Manager',        'Inquiries, quotations and pricing', 1),
                                                           ('FINANCE_MANAGER',     'Finance Manager',      'Payments, invoices and refunds', 1),
                                                           ('DISPATCH_MANAGER',    'Dispatch Manager',     'Packaging, shipments and delivery', 1),
                                                           ('QC_INSPECTOR',        'Quality Inspector',    'Quality inspections and rework decisions', 1),
                                                           ('PRODUCTION_OPERATOR', 'Production Operator',  'Shop-floor stage updates on assigned work', 1);

INSERT INTO permissions (code, description) VALUES
                                                ('USER_VIEW',   'View user accounts'),
                                                ('USER_MANAGE', 'Create staff accounts and edit users'),
                                                ('USER_BLOCK',  'Block and unblock accounts'),
                                                ('ROLE_MANAGE', 'Edit roles and their permissions'),
                                                ('AUDIT_VIEW',  'View audit logs');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('USER_VIEW', 'AUDIT_VIEW') WHERE r.code = 'FACTORY_MANAGER';