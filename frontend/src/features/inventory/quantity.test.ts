import { describe, expect, it } from 'vitest'
import { formatQuantity, formatSigned, plural } from './quantity'

describe('formatQuantity', () => {
    it('shows whole units and up to three decimals', () => {
        expect(formatQuantity(12)).toBe('12')
        expect(formatQuantity(12.5)).toBe('12.5')
        expect(formatQuantity(1.23456)).toBe('1.235')
    })

    it('groups digits the Indian way', () => {
        expect(formatQuantity(1234567)).toBe('12,34,567')
    })

    it('shows plain zero, never negative zero', () => {
        expect(formatQuantity(0)).toBe('0')
        expect(formatQuantity(-0)).toBe('0')
    })
})

describe('formatSigned', () => {
    it('always shows the direction of a change', () => {
        expect(formatSigned(5)).toBe('+5')
        expect(formatSigned(-2.5)).toBe('-2.5')
        expect(formatSigned(0)).toBe('0')
        expect(formatSigned(-0)).toBe('0')
    })
})

describe('plural', () => {
    it('uses the singular only for exactly one', () => {
        expect(plural(1, 'item')).toBe('1 item')
        expect(plural(0, 'item')).toBe('0 items')
        expect(plural(3, 'stock row')).toBe('3 stock rows')
    })
})