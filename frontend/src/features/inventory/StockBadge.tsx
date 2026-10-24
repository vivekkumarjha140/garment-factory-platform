import { STOCK_LEVEL_LABEL, stockLevel, type StockLevel } from './stockStatus'

const STYLES: Record<StockLevel, string> = {
    out: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-200',
    low: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200',
    ok: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-200',
}

/** The text says the status, so colour is never the only signal. */
export function StockBadge({ available, lowStockThreshold }: { available: number; lowStockThreshold: number }) {
    const level = stockLevel({ available, lowStockThreshold })
    return (
        <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STYLES[level]}`}>
            {STOCK_LEVEL_LABEL[level]}
        </span>
    )
}