import type { ZodType } from 'zod'
import { describe, expect, it } from 'vitest'
import {
    adjustmentSchema,
    auditNotesSchema,
    damageSchema,
    measuredCountSchema,
    receiptSchema,
    thresholdSchema,
    toAdjustStockRequest,
    toDamageStockRequest,
    toReceiveStockRequest,
    unitCountSchema,
} from './schemas'

function firstMessage(schema: ZodType, value: unknown): string | undefined {
    const result = schema.safeParse(value)
    return result.success ? undefined : result.error.issues[0]?.message
}

describe('adjustmentSchema', () => {
    it('accepts a positive or negative whole number with a reason', () => {
        expect(adjustmentSchema.safeParse({ delta: '5', reason: 'found one' }).success).toBe(true)
        expect(adjustmentSchema.safeParse({ delta: '-3', reason: 'cycle count' }).success).toBe(true)
        expect(adjustmentSchema.safeParse({ delta: '+2', reason: 'recount' }).success).toBe(true)
    })

    it('rejects zero, decimals and text', () => {
        expect(firstMessage(adjustmentSchema, { delta: '0', reason: 'x' })).toBe('The change cannot be zero')
        expect(firstMessage(adjustmentSchema, { delta: '1.5', reason: 'x' })).toBe('Enter a whole number, like 5 or -3')
        expect(firstMessage(adjustmentSchema, { delta: 'abc', reason: 'x' })).toBe('Enter a whole number, like 5 or -3')
    })

    it('requires a reason, because every adjustment is written to the ledger', () => {
        expect(firstMessage(adjustmentSchema, { delta: '5', reason: '   ' })).toBe('Give a reason')
    })

    it('stops absurd quantities before the server has to', () => {
        expect(firstMessage(adjustmentSchema, { delta: '99999999', reason: 'x' })).toBe('That is more than the system accepts')
    })

    it('converts to the request body with a trimmed reason', () => {
        expect(toAdjustStockRequest({ delta: '-3', reason: ' cycle count ' })).toEqual({ delta: -3, reason: 'cycle count' })
    })
})

describe('damageSchema', () => {
    it('needs at least one damaged unit', () => {
        expect(firstMessage(damageSchema, { quantity: '0', reason: 'x' })).toBe('Enter at least 1')
        expect(damageSchema.safeParse({ quantity: '2', reason: 'water damage' }).success).toBe(true)
    })

    it('does not accept a negative quantity', () => {
        expect(firstMessage(damageSchema, { quantity: '-1', reason: 'x' })).toBe('Enter a whole number, like 5')
    })

    it('converts to the request body', () => {
        expect(toDamageStockRequest({ quantity: '2', reason: 'water damage' })).toEqual({ quantity: 2, reason: 'water damage' })
    })
})

describe('receiptSchema', () => {
    const base = { quantity: '10', unitCost: '', supplierName: '', supplierReference: '', notes: '' }

    it('accepts a receipt with only a quantity', () => {
        expect(receiptSchema.safeParse(base).success).toBe(true)
    })

    it('validates the unit cost format', () => {
        expect(receiptSchema.safeParse({ ...base, unitCost: '250.50' }).success).toBe(true)
        expect(firstMessage(receiptSchema, { ...base, unitCost: '12.12345' })).toBe('Enter an amount like 250 or 250.50')
        expect(firstMessage(receiptSchema, { ...base, unitCost: '-5' })).toBe('Enter an amount like 250 or 250.50')
    })

    it('turns blank optional fields into nulls for the API', () => {
        expect(toReceiveStockRequest(base)).toEqual({
            quantity: 10,
            unitCost: null,
            supplierName: null,
            supplierReference: null,
            notes: null,
        })
    })

    it('keeps the optional details that were filled in', () => {
        expect(
            toReceiveStockRequest({ quantity: '4', unitCost: '199.99', supplierName: ' Rajasthan Textiles ', supplierReference: 'INV-9', notes: '' }),
        ).toEqual({ quantity: 4, unitCost: 199.99, supplierName: 'Rajasthan Textiles', supplierReference: 'INV-9', notes: null })
    })
})

describe('thresholdSchema', () => {
    it('accepts zero and rejects negatives and decimals', () => {
        expect(thresholdSchema.safeParse({ lowStockThreshold: '0' }).success).toBe(true)
        expect(thresholdSchema.safeParse({ lowStockThreshold: '-1' }).success).toBe(false)
        expect(thresholdSchema.safeParse({ lowStockThreshold: '2.5' }).success).toBe(false)
    })
})

describe('audit counts', () => {
    it('counts finished goods in whole units, including zero', () => {
        expect(unitCountSchema.safeParse({ countedQuantity: '0' }).success).toBe(true)
        expect(unitCountSchema.safeParse({ countedQuantity: '8.5' }).success).toBe(false)
    })

    it('counts raw materials with up to 3 decimals', () => {
        expect(measuredCountSchema.safeParse({ countedQuantity: '14.5' }).success).toBe(true)
        expect(measuredCountSchema.safeParse({ countedQuantity: '14.5555' }).success).toBe(false)
        expect(measuredCountSchema.safeParse({ countedQuantity: '-1' }).success).toBe(false)
    })

    it('keeps audit notes short', () => {
        expect(auditNotesSchema.safeParse({ notes: '' }).success).toBe(true)
        expect(auditNotesSchema.safeParse({ notes: 'x'.repeat(501) }).success).toBe(false)
    })
})