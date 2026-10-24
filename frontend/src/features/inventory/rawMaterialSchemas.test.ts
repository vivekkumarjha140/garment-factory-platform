import type { ZodType } from 'zod'
import { describe, expect, it } from 'vitest'
import {
    categoryLabel,
    rawAdjustmentSchema,
    rawMaterialSchema,
    rawReceiptSchema,
    toAdjustRawMaterialRequest,
    toCreateRawMaterialRequest,
    toReceiveRawMaterialRequest,
} from './rawMaterialSchemas'

function firstMessage(schema: ZodType, value: unknown): string | undefined {
    const result = schema.safeParse(value)
    return result.success ? undefined : result.error.issues[0]?.message
}

const VALID = { code: 'FAB-DENIM', name: 'Denim', category: 'FABRIC', uom: 'METER', lowStockThreshold: '25', unitCost: '210.5' }

describe('rawMaterialSchema', () => {
    it('accepts a complete raw material and converts it for the API', () => {
        expect(rawMaterialSchema.safeParse(VALID).success).toBe(true)
        expect(toCreateRawMaterialRequest(VALID)).toEqual({
            code: 'FAB-DENIM',
            name: 'Denim',
            category: 'FABRIC',
            uom: 'METER',
            lowStockThreshold: 25,
            unitCost: 210.5,
        })
    })

    it('turns blank optional numbers into nulls', () => {
        expect(toCreateRawMaterialRequest({ ...VALID, lowStockThreshold: '', unitCost: '' })).toMatchObject({
            lowStockThreshold: null,
            unitCost: null,
        })
    })

    it('requires a known category', () => {
        expect(firstMessage(rawMaterialSchema, { ...VALID, category: '' })).toBe('Choose a category')
        expect(firstMessage(rawMaterialSchema, { ...VALID, category: 'WOOD' })).toBe('Choose a category')
    })

    it('rejects awkward codes and missing names', () => {
        expect(firstMessage(rawMaterialSchema, { ...VALID, code: 'bad code' })).toBe('Use letters, digits, dots, hyphens or underscores')
        expect(firstMessage(rawMaterialSchema, { ...VALID, name: '  ' })).toBe('Enter a name')
    })

    it('limits the threshold to three decimals', () => {
        expect(firstMessage(rawMaterialSchema, { ...VALID, lowStockThreshold: '1.2345' })).toBe('Enter a quantity, like 50 or 12.5')
    })
})

describe('rawReceiptSchema', () => {
    const base = { quantity: '12.5', unitCost: '', supplierName: '', supplierReference: '', notes: '' }

    it('accepts decimal quantities and converts them', () => {
        expect(rawReceiptSchema.safeParse(base).success).toBe(true)
        expect(toReceiveRawMaterialRequest(base)).toEqual({
            quantity: 12.5,
            unitCost: null,
            supplierName: null,
            supplierReference: null,
            notes: null,
        })
    })

    it('needs more than zero', () => {
        expect(firstMessage(rawReceiptSchema, { ...base, quantity: '0' })).toBe('Enter more than 0')
        expect(firstMessage(rawReceiptSchema, { ...base, quantity: '-1' })).toBe('Enter a quantity, like 50 or 12.5')
    })
})

describe('rawAdjustmentSchema', () => {
    it('accepts signed decimals and requires a reason', () => {
        expect(rawAdjustmentSchema.safeParse({ delta: '-2.5', reason: 'spoiled' }).success).toBe(true)
        expect(firstMessage(rawAdjustmentSchema, { delta: '2', reason: ' ' })).toBe('Give a reason')
    })

    it('rejects a zero change', () => {
        expect(firstMessage(rawAdjustmentSchema, { delta: '0', reason: 'x' })).toBe('The change cannot be zero')
    })

    it('converts to the request body', () => {
        expect(toAdjustRawMaterialRequest({ delta: '-2.5', reason: ' spoiled ' })).toEqual({ delta: -2.5, reason: 'spoiled' })
    })
})

describe('categoryLabel', () => {
    it('shows a readable name', () => {
        expect(categoryLabel('BLANK_GARMENT')).toBe('Blank garment')
    })
})