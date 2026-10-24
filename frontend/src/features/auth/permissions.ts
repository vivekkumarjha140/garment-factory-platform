/** Permission codes seeded by the backend. Each later phase adds its own. */
export const Permission = {
    USER_VIEW: 'USER_VIEW',
    USER_MANAGE: 'USER_MANAGE',
    USER_BLOCK: 'USER_BLOCK',
    ROLE_MANAGE: 'ROLE_MANAGE',
    AUDIT_VIEW: 'AUDIT_VIEW',
    STOCK_VIEW: 'STOCK_VIEW',
    STOCK_ADJUST: 'STOCK_ADJUST',
    INVENTORY_AUDIT: 'INVENTORY_AUDIT',
} as const