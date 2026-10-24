import { describe, expect, it } from 'vitest'
import { signedQuantity, stockLevel, summarize, variantLabel } from './stockStatus'

describe('stockLevel', () => {
    it('is out when nothing is available, even if stock is on hand but fully reserved', () => {
        expect(stockLevel({ available: 0, lowStockThreshold: 5 })).toBe('out')
    })

    it('is low at or below the threshold', () => {
        expect(stockLevel({ available: 5, lowStockThreshold: 5 })).toBe('low')
        expect(stockLevel({ available: 1, lowStockThreshold: 5 })).toBe('low')
    })

    it('is ok above the threshold', () => {
        expect(stockLevel({ available: 6, lowStockThreshold: 5 })).toBe('ok')
    })

    it('treats a zero threshold as "only warn when empty"', () => {
        expect(stockLevel({ available: 1, lowStockThreshold: 0 })).toBe('ok')
    })
})

describe('signedQuantity', () => {
    it('always shows the direction of a change', () => {
        expect(signedQuantity(5)).toBe('+5')
        expect(signedQuantity(-3)).toBe('-3')
        expect(signedQuantity(0)).toBe('0')
    })
})

describe('variantLabel', () => {
    it('shows product, size and colour', () => {
        expect(variantLabel({ variantId: 7, productName: 'Round Neck Tee', size: 'M', colorName: 'Red' })).toBe(
            'Round Neck Tee (M / Red)',
        )
    })

    it('falls back to the id when the catalog no longer knows the variant', () => {
        expect(variantLabel({ variantId: 7, productName: null, size: null, colorName: null })).toBe('Variant #7')
    })

    it('copes with a missing size or colour', () => {
        expect(variantLabel({ variantId: 7, productName: 'Tote', size: null, colorName: null })).toBe('Tote')
    })
})

describe('summarize', () => {
    it('counts out-of-stock and low-stock rows', () => {
        const rows = [
            { available: 0, lowStockThreshold: 5 },
            { available: 3, lowStockThreshold: 5 },
            { available: 40, lowStockThreshold: 5 },
            { available: 5, lowStockThreshold: 5 },
        ]

        expect(summarize(rows)).toEqual({ total: 4, out: 1, low: 2 })
    })

    it('handles an empty list', () => {
        expect(summarize([])).toEqual({ total: 0, out: 0, low: 0 })
    })
})