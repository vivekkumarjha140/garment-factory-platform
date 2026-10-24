import type { AuditStatus, StockItemType } from '../../types/inventory'

export const AUDIT_STATUS_LABEL: Record<AuditStatus, string> = {
    IN_PROGRESS: 'In progress',
    COMPLETED: 'Completed',
    CANCELLED: 'Cancelled',
}

export const ITEM_TYPE_LABEL: Record<StockItemType, string> = {
    FINISHED_GOOD: 'Finished good',
    RAW_MATERIAL: 'Raw material',
}