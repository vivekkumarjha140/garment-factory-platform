export const orderKeys = {
    all: ['orders'] as const,
    list: (page: number) => ['orders', 'list', page] as const,
    detail: (orderNumber: string) => ['orders', 'detail', orderNumber] as const,
}