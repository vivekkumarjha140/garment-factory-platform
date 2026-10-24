import type { MovementType, StockItem } from '../../types/inventory'

export type StockLevel = 'out' | 'low' | 'ok'

export const STOCK_LEVEL_LABEL: Record<StockLevel, string> = {
    out: 'Out of stock',
    low: 'Low stock',
    ok: 'In stock',
}

export const MOVEMENT_LABEL: Record<MovementType, string> = {
    RECEIPT: 'Goods receipt',
    RESERVATION: 'Reserved for an order',
    DEDUCTION: 'Shipped',
    RELEASE: 'Reservation released',
    ADJUSTMENT: 'Manual adjustment',
    DAMAGE: 'Marked damaged',
}

type Counters = Pick<StockItem, 'available' | 'lowStockThreshold'>

export function stockLevel(item: Counters): StockLevel {
    if (item.available <= 0) return 'out'
    if (item.available <= item.lowStockThreshold) return 'low'
    return 'ok'
}

/** "+5", "-3", "0": quantity changes always show their direction. */
export function signedQuantity(value: number): string {
    return value > 0 ? `+${value}` : String(value)
}

/** The headline for a table row: product, size and colour, or a fallback when the catalog lost the variant. */
export function variantLabel(item: Pick<StockItem, 'variantId' | 'productName' | 'size' | 'colorName'>): string {
    if (!item.productName) return `Variant #${item.variantId}`
    const options = [item.size, item.colorName].filter(Boolean).join(' / ')
    return options ? `${item.productName} (${options})` : item.productName
}

export interface StockSummary {
    total: number
    out: number
    low: number
}

/** Numbers for the strip above the table, from the rows currently loaded. */
export function summarize(items: Counters[]): StockSummary {
    return items.reduce<StockSummary>(
        (summary, item) => {
            const level = stockLevel(item)
            return {
                total: summary.total + 1,
                out: summary.out + (level === 'out' ? 1 : 0),
                low: summary.low + (level === 'low' ? 1 : 0),
            }
        },
        { total: 0, out: 0, low: 0 },
    )
}