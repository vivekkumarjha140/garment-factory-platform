import type {
    AdjustRawMaterialRequest,
    AdjustStockRequest,
    AuditCompletion,
    AuditDetail,
    AuditLine,
    AuditStatus,
    AuditSummary,
    CreateRawMaterialRequest,
    DamageStockRequest,
    PageResponse,
    RawMaterial,
    RawMaterialCategory,
    ReceiveRawMaterialRequest,
    ReceiveStockRequest,
    StockItem,
    StockMovement,
    StockReceipt,
    StockSnapshot,
    UninitializedVariant,
} from '../types/inventory'
import { call } from './call'
import { apiClient } from './client'

const BASE = '/inventory'
const id = encodeURIComponent

export interface StockListParams {
    q?: string
    lowStockOnly?: boolean
    page?: number
    size?: number
}

export interface RawMaterialListParams {
    category?: RawMaterialCategory
    lowStockOnly?: boolean
    page?: number
    size?: number
}

export interface AuditListParams {
    status?: AuditStatus
    page?: number
    size?: number
}

/** Drops empty values so URLs stay clean. false and 0 are real answers and are kept. */
export function cleanParams(params: object): Record<string, string | number | boolean> {
    const result: Record<string, string | number | boolean> = {}
    for (const [key, value] of Object.entries(params as Record<string, unknown>)) {
        if (value === undefined || value === null) continue
        if (typeof value === 'string') {
            const trimmed = value.trim()
            if (trimmed) result[key] = trimmed
        } else if (typeof value === 'number' || typeof value === 'boolean') {
            result[key] = value
        }
    }
    return result
}

export const inventoryApi = {
    // ---------- finished goods ----------
    listStock: (params: StockListParams = {}) =>
        call(apiClient.get<PageResponse<StockItem>>(`${BASE}/stock`, { params: cleanParams(params) })),

    getStock: (variantId: number) => call(apiClient.get<StockItem>(`${BASE}/stock/${variantId}`)),

    stockMovements: (variantId: number, page = 0, size = 20) =>
        call(apiClient.get<PageResponse<StockMovement>>(`${BASE}/stock/${variantId}/movements`, { params: { page, size } })),

    receipts: (page = 0, size = 20) =>
        call(apiClient.get<PageResponse<StockReceipt>>(`${BASE}/receipts`, { params: { page, size } })),

    uninitializedVariants: (q?: string, limit = 20) =>
        call(apiClient.get<UninitializedVariant[]>(`${BASE}/stock/uninitialized`, { params: cleanParams({ q, limit }) })),

    initializeStock: (variantId: number, lowStockThreshold?: number) =>
        call(apiClient.post<StockSnapshot>(`${BASE}/stock`, { variantId, lowStockThreshold })),

    initializeMissing: (lowStockThreshold?: number) =>
        call(apiClient.post<{ created: number }>(`${BASE}/stock/initialize-missing`, { lowStockThreshold })),

    updateThreshold: (variantId: number, lowStockThreshold: number) =>
        call(apiClient.patch<StockSnapshot>(`${BASE}/stock/${variantId}/threshold`, { lowStockThreshold })),

    receiveStock: (variantId: number, body: ReceiveStockRequest) =>
        call(apiClient.post<StockSnapshot>(`${BASE}/stock/${variantId}/receipts`, body)),

    adjustStock: (variantId: number, body: AdjustStockRequest) =>
        call(apiClient.post<StockSnapshot>(`${BASE}/stock/${variantId}/adjustments`, body)),

    damageStock: (variantId: number, body: DamageStockRequest) =>
        call(apiClient.post<StockSnapshot>(`${BASE}/stock/${variantId}/damage`, body)),

    // ---------- raw materials ----------
    listRawMaterials: (params: RawMaterialListParams = {}) =>
        call(apiClient.get<PageResponse<RawMaterial>>(`${BASE}/raw-materials`, { params: cleanParams(params) })),

    createRawMaterial: (body: CreateRawMaterialRequest) =>
        call(apiClient.post<RawMaterial>(`${BASE}/raw-materials`, body)),

    receiveRawMaterial: (rawMaterialId: number, body: ReceiveRawMaterialRequest) =>
        call(apiClient.post<RawMaterial>(`${BASE}/raw-materials/${rawMaterialId}/receipts`, body)),

    adjustRawMaterial: (rawMaterialId: number, body: AdjustRawMaterialRequest) =>
        call(apiClient.post<RawMaterial>(`${BASE}/raw-materials/${rawMaterialId}/adjustments`, body)),

    rawMaterialMovements: (rawMaterialId: number, page = 0, size = 20) =>
        call(apiClient.get<PageResponse<StockMovement>>(`${BASE}/raw-materials/${rawMaterialId}/movements`, { params: { page, size } })),

    // ---------- inventory audits ----------
    listAudits: (params: AuditListParams = {}) =>
        call(apiClient.get<PageResponse<AuditSummary>>(`${BASE}/audits`, { params: cleanParams(params) })),

    startAudit: (notes?: string) => call(apiClient.post<AuditSummary>(`${BASE}/audits`, { notes })),

    getAudit: (publicId: string) => call(apiClient.get<AuditDetail>(`${BASE}/audits/${id(publicId)}`)),

    countFinishedGood: (publicId: string, variantId: number, countedQuantity: number) =>
        call(apiClient.post<AuditLine>(`${BASE}/audits/${id(publicId)}/counts/finished-goods`, { variantId, countedQuantity })),

    countRawMaterial: (publicId: string, rawMaterialId: number, countedQuantity: number) =>
        call(apiClient.post<AuditLine>(`${BASE}/audits/${id(publicId)}/counts/raw-materials`, { rawMaterialId, countedQuantity })),

    completeAudit: (publicId: string, applyAdjustments: boolean) =>
        call(apiClient.post<AuditCompletion>(`${BASE}/audits/${id(publicId)}/complete`, { applyAdjustments })),

    cancelAudit: (publicId: string) => call(apiClient.post<AuditSummary>(`${BASE}/audits/${id(publicId)}/cancel`)),
}