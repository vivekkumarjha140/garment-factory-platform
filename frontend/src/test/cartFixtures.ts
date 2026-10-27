import type { Cart, CartLine, CartTotals } from '../types/cart'

export function cartLine(overrides: Partial<CartLine> = {}): CartLine {
    return {
        itemId: 1,
        variantId: 'v1',
        productId: 1,
        productName: 'Classic Tee',
        productSlug: 'classic-tee',
        size: 'M',
        colorName: 'Navy',
        quantity: 2,
        unitPrice: 500,
        lineTotal: 1000,
        gstRate: 5,
        gstAmount: 50,
        availableStock: 10,
        issue: null,
        ...overrides,
    }
}

type CartOverrides = Partial<Omit<Cart, 'totals'>> & {
    totals?: Partial<CartTotals>
}

/** A cart whose totals follow from its lines (no discount, no shipping) unless overridden. */
export function cartOf(
    lines: CartLine[] = [cartLine()],
    overrides: CartOverrides = {},
): Cart {
    const { totals, ...rest } = overrides

    const subtotal = lines.reduce((sum, line) => sum + line.lineTotal, 0)
    const gst = lines.reduce((sum, line) => sum + line.gstAmount, 0)

    return {
        cartId: 1,
        lines,
        coupon: null,
        couponError: null,
        shipping: null,
        shippingError: null,
        checkoutReady: lines.length > 0 && lines.every((line) => line.issue === null),
        shippingAddressId: null,
        ...rest,
        totals: {
            totalQuantity: lines.reduce((sum, line) => sum + line.quantity, 0),
            subtotal,
            discount: 0,
            taxableAmount: subtotal,
            gstAmount: gst,
            shippingCharge: null,
            shippingCalculated: false,
            total: subtotal + gst,
            ...totals,
        },
    }
}