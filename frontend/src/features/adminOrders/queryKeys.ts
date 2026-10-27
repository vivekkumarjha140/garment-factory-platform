import type { AdminOrderFilters } from '../../api/adminOrders'

export const adminOrderKeys = {
    lists: ['admin-orders', 'list'] as const,
    list: (filters: AdminOrderFilters) => ['admin-orders', 'list', filters] as const,
    detail: (orderNumber: string) => ['admin-orders', 'detail', orderNumber] as const,
}