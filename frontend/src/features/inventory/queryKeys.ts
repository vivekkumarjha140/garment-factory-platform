import type { AuditListParams, RawMaterialListParams, StockListParams } from '../../api/inventory'

/** All inventory queries live under one root, so a stock change can refresh everything at once. */
export const inventoryKeys = {
    all: ['inventory'] as const,
    stockLists: () => [...inventoryKeys.all, 'stock', 'list'] as const,
    stockList: (params: StockListParams) => [...inventoryKeys.stockLists(), params] as const,
    stock: (variantId: number) => [...inventoryKeys.all, 'stock', variantId] as const,
    movements: (variantId: number, page: number) => [...inventoryKeys.stock(variantId), 'movements', page] as const,
    rawMovements: (rawMaterialId: number, page: number) => [...inventoryKeys.all, 'raw-movements', rawMaterialId, page] as const,
    uninitialized: (q: string) => [...inventoryKeys.all, 'uninitialized', q] as const,
    receipts: (page: number) => [...inventoryKeys.all, 'receipts', page] as const,
    rawMaterials: (params: RawMaterialListParams) => [...inventoryKeys.all, 'raw-materials', params] as const,
    audits: (params: AuditListParams) => [...inventoryKeys.all, 'audits', params] as const,
    audit: (publicId: string) => [...inventoryKeys.all, 'audit', publicId] as const,
}