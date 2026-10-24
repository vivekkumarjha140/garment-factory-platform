export type StockItemType = 'FINISHED_GOOD' | 'RAW_MATERIAL'
export type MovementType = 'RECEIPT' | 'RESERVATION' | 'DEDUCTION' | 'RELEASE' | 'ADJUSTMENT' | 'DAMAGE'
export type RawMaterialCategory = 'FABRIC' | 'BLANK_GARMENT' | 'CONSUMABLE' | 'PACKAGING'
export type AuditStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export interface PageResponse<T> {
    items: T[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

/** One dashboard row: stock counters plus the catalog variant's details (null if the catalog lost the variant). */
export interface StockItem {
    variantId: number
    sku: string | null
    productName: string | null
    size: string | null
    colorName: string | null
    variantStatus: string | null
    publicId: string
    onHand: number
    reserved: number
    damaged: number
    available: number
    lowStockThreshold: number
    lowStock: boolean
    version: number
    updatedAt: string
}

/** What the write endpoints (receive, adjust, damage, threshold, initialise) return. */
export interface StockSnapshot {
    publicId: string
    variantId: number
    onHand: number
    reserved: number
    damaged: number
    available: number
    lowStockThreshold: number
    lowStock: boolean
    version: number
    updatedAt: string
}

export interface UninitializedVariant {
    variantId: number
    sku: string
    productName: string
    size: string
    colorName: string
}

export interface StockMovement {
    publicId: string
    itemType: StockItemType
    variantId: number | null
    rawMaterialId: number | null
    movementType: MovementType
    onHandDelta: number
    reservedDelta: number
    damagedDelta: number
    balanceOnHandAfter: number
    referenceType: string | null
    referenceId: string | null
    reason: string | null
    actorUserId: number | null
    createdAt: string
}

export interface StockReceipt {
    publicId: string
    receiptNumber: string
    itemType: StockItemType
    variantId: number | null
    rawMaterialId: number | null
    quantity: number
    unitCost: number | null
    supplierName: string | null
    supplierReference: string | null
    notes: string | null
    receivedAt: string
    receivedByUserId: number
}

export interface RawMaterial {
    id: number
    publicId: string
    code: string
    name: string
    category: RawMaterialCategory
    uom: string
    onHand: number
    reserved: number
    available: number
    lowStockThreshold: number
    lowStock: boolean
    unitCost: number
    active: boolean
    version: number
}

export interface AuditSummary {
    publicId: string
    auditNumber: string
    status: AuditStatus
    notes: string | null
    startedByUserId: number
    startedAt: string
    completedAt: string | null
}

export interface AuditLine {
    id: number
    itemType: StockItemType
    variantId: number | null
    rawMaterialId: number | null
    itemName: string
    sku: string | null
    systemQuantity: number
    countedQuantity: number
    variance: number
    adjustmentMovementId: number | null
}

export interface AuditDetail {
    audit: AuditSummary
    lines: AuditLine[]
    linesWithVariance: number
}

export interface AuditCompletion {
    audit: AuditSummary
    adjustmentsApplied: number
}

// ---------- request bodies ----------

export interface ReceiveStockRequest {
    quantity: number
    unitCost: number | null
    supplierName: string | null
    supplierReference: string | null
    notes: string | null
}

export interface AdjustStockRequest {
    delta: number
    reason: string
}

export interface DamageStockRequest {
    quantity: number
    reason: string
}

export interface CreateRawMaterialRequest {
    code: string
    name: string
    category: RawMaterialCategory
    uom: string
    lowStockThreshold: number | null
    unitCost: number | null
}

export interface ReceiveRawMaterialRequest {
    quantity: number
    unitCost: number | null
    supplierName: string | null
    supplierReference: string | null
    notes: string | null
}

export interface AdjustRawMaterialRequest {
    delta: number
    reason: string
}