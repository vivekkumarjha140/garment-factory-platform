import type { Cart, CartLine } from '../../types/cart'

export const MAX_LINE_QUANTITY = 99

export function badgeCount(cart: Cart | undefined | null): number {
    if (!cart) return 0

    return cart.lines.reduce(
        (total, line) => total + (line.issue === null ? line.quantity : 0),
        0,
    )
}

export function canDecrease(line: CartLine): boolean {
    return line.quantity > 1 && line.issue !== 'UNAVAILABLE'
}

export function canIncrease(line: CartLine): boolean {
    return (
        line.quantity < MAX_LINE_QUANTITY &&
        line.quantity < line.availableStock &&
        line.issue === null
    )
}

export function issueMessage(line: CartLine): string | null {
    switch (line.issue) {
        case 'UNAVAILABLE':
            return 'This product variant is no longer available.'

        case 'OUT_OF_STOCK':
            return 'This item is out of stock.'

        case 'INSUFFICIENT_STOCK':
            return `Only ${line.availableStock} left. Please reduce the quantity.`

        case 'QUANTITY_LIMIT':
            return 'The requested quantity exceeds the available stock.'

        default:
            return null
    }
}

export function deliveryEstimate(cart: Cart): string | null {
    const shipping = cart.shipping

    if (!shipping) return null

    const { estimatedDaysMin, estimatedDaysMax } = shipping

    if (estimatedDaysMin === estimatedDaysMax) {
        return `${estimatedDaysMin} days`
    }

    return `${estimatedDaysMin}–${estimatedDaysMax} days`
}