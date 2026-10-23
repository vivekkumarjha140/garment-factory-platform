const WHOLE = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
const WITH_PAISE = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })

/** Rupees with Indian digit grouping: whole amounts without decimals, others with two. */
export function formatPrice(value: number): string {
    return Number.isInteger(value) ? WHOLE.format(value) : WITH_PAISE.format(value)
}

/** Whole-number percentage off the MRP; 0 when there is nothing to show. */
export function discountPercent(price: number, mrp: number): number {
    if (!(mrp > price) || mrp <= 0) return 0
    return Math.round(((mrp - price) / mrp) * 100)
}