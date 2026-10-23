import { describe, expect, it } from 'vitest'
import type { PublicVariant } from '../../types/catalog'
import {
    displayedPrice,
    findVariant,
    isColorAvailable,
    isSizeAvailable,
    sortedSizes,
    uniqueColors,
} from './variantSelection'

function variant(id: string, size: string, colorName: string, price: number): PublicVariant {
    return { id, size, colorName, colorHex: null, price }
}

const NAVY_M = variant('v1', 'M', 'Navy', 499)
const NAVY_L = variant('v2', 'L', 'Navy', 549)
const RED_M = variant('v3', 'M', 'Red', 499)
const ALL = [NAVY_M, NAVY_L, RED_M]

describe('sortedSizes', () => {
    it('orders sizes from small to large, then kids sizes, then unknown ones', () => {
        const variants = ['XL', 'S', 'FREE', 'M', '4-5Y', 'Odd'].map((size, index) => variant(`v${index}`, size, 'Navy', 100))

        expect(sortedSizes(variants)).toEqual(['S', 'M', 'XL', 'FREE', '4-5Y', 'Odd'])
    })
})

describe('uniqueColors', () => {
    it('lists each colour once, ignoring case, in order of first appearance', () => {
        const colors = uniqueColors([NAVY_M, variant('x', 'S', 'navy', 1), RED_M, NAVY_L])

        expect(colors.map((color) => color.name)).toEqual(['Navy', 'Red'])
    })
})

describe('findVariant', () => {
    it('needs both a size and a colour, and ignores the colour case', () => {
        expect(findVariant(ALL, 'M', 'navy')).toBe(NAVY_M)
        expect(findVariant(ALL, 'L', 'Red')).toBeUndefined()
        expect(findVariant(ALL, null, 'Navy')).toBeUndefined()
        expect(findVariant(ALL, 'M', null)).toBeUndefined()
    })
})

describe('availability', () => {
    it('checks a size against the chosen colour', () => {
        expect(isSizeAvailable(ALL, 'L', 'Navy')).toBe(true)
        expect(isSizeAvailable(ALL, 'L', 'Red')).toBe(false)
        expect(isSizeAvailable(ALL, 'L', null)).toBe(true)
    })

    it('checks a colour against the chosen size', () => {
        expect(isColorAvailable(ALL, 'Red', 'M')).toBe(true)
        expect(isColorAvailable(ALL, 'Red', 'L')).toBe(false)
        expect(isColorAvailable(ALL, 'Red', null)).toBe(true)
    })
})

describe('displayedPrice', () => {
    it('shows the chosen variant price', () => {
        expect(displayedPrice(ALL, NAVY_L, 499)).toEqual({ price: 549, isFrom: false })
    })

    it('shows the lowest price as "from" while prices differ', () => {
        expect(displayedPrice(ALL, undefined, 499)).toEqual({ price: 499, isFrom: true })
    })

    it('does not say "from" when every variant costs the same', () => {
        expect(displayedPrice([NAVY_M, RED_M], undefined, 499)).toEqual({ price: 499, isFrom: false })
    })

    it('falls back to the product from-price without variants', () => {
        expect(displayedPrice([], undefined, 499)).toEqual({ price: 499, isFrom: false })
    })
})