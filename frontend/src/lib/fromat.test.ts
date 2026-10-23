import { describe, expect, it } from 'vitest'
import { discountPercent, formatPrice } from './format'

describe('formatPrice', () => {
    it('shows whole rupees without decimals and uses Indian digit grouping', () => {
        expect(formatPrice(499)).toBe('₹499')
        expect(formatPrice(125000)).toBe('₹1,25,000')
    })

    it('shows paise when the amount has them', () => {
        expect(formatPrice(1299.5)).toBe('₹1,299.50')
    })
})

describe('discountPercent', () => {
    it('rounds the percentage off the MRP', () => {
        expect(discountPercent(499, 699)).toBe(29)
        expect(discountPercent(50, 100)).toBe(50)
    })

    it('is zero when the price is not below the MRP', () => {
        expect(discountPercent(500, 500)).toBe(0)
        expect(discountPercent(600, 500)).toBe(0)
        expect(discountPercent(0, 0)).toBe(0)
    })
})