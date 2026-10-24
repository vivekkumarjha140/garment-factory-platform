const QUANTITY = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 3 })

/** Stock quantities: whole units or up to 3 decimals, with Indian digit grouping. */
export function formatQuantity(value: number): string {
    return value === 0 ? '0' : QUANTITY.format(value)
}

/** A change in quantity: always shows its direction. */
export function formatSigned(value: number): string {
    if (value === 0) return '0'
    return value > 0 ? `+${QUANTITY.format(value)}` : QUANTITY.format(value)
}

export function formatDateTime(value: string): string {
    return new Date(value).toLocaleString()
}

export function plural(count: number, singular: string, pluralForm = `${singular}s`): string {
    return `${count} ${count === 1 ? singular : pluralForm}`
}