import { z } from 'zod'
import type {
    AdjustStockRequest,
    DamageStockRequest,
    ReceiveStockRequest,
} from '../../types/inventory'

const MAX_QUANTITY = 1_000_000
const WHOLE = /^[+-]?\d+$/
const UNSIGNED_WHOLE = /^\d+$/
const MONEY = /^(\d+(\.\d{1,4})?)?$/
const DECIMAL_3 = /^\d+(\.\d{1,3})?$/

const reason = z.string().trim().min(1, 'Give a reason').max(500, 'Keep the reason under 500 characters')

/** A whole number of at least 1. Later checks stay quiet when the format check has already failed. */
const positiveWhole = z
    .string()
    .trim()
    .regex(UNSIGNED_WHOLE, 'Enter a whole number, like 5')
    .refine((value) => !UNSIGNED_WHOLE.test(value) || Number(value) >= 1, 'Enter at least 1')
    .refine((value) => !UNSIGNED_WHOLE.test(value) || Number(value) <= MAX_QUANTITY, 'That is more than the system accepts')

export const adjustmentSchema = z.object({
    delta: z
        .string()
        .trim()
        .regex(WHOLE, 'Enter a whole number, like 5 or -3')
        .refine((value) => !WHOLE.test(value) || Number(value) !== 0, 'The change cannot be zero')
        .refine((value) => !WHOLE.test(value) || Math.abs(Number(value)) <= MAX_QUANTITY, 'That is more than the system accepts'),
    reason,
})

export const damageSchema = z.object({ quantity: positiveWhole, reason })

export const receiptSchema = z.object({
    quantity: positiveWhole,
    unitCost: z.string().trim().regex(MONEY, 'Enter an amount like 250 or 250.50'),
    supplierName: z.string().trim().max(150, 'Keep the supplier name under 150 characters'),
    supplierReference: z.string().trim().max(100, 'Keep the reference under 100 characters'),
    notes: z.string().trim().max(500, 'Keep the notes under 500 characters'),
})

export const thresholdSchema = z.object({
    lowStockThreshold: z
        .string()
        .trim()
        .regex(UNSIGNED_WHOLE, 'Enter a whole number, 0 or more')
        .refine((value) => !UNSIGNED_WHOLE.test(value) || Number(value) <= MAX_QUANTITY, 'That is more than the system accepts'),
})

/** Finished goods are counted in whole units. */
export const unitCountSchema = z.object({
    countedQuantity: z.string().trim().regex(UNSIGNED_WHOLE, 'Enter the counted units as a whole number, 0 or more'),
})

/** Raw materials can be counted in fractions (metres, kilograms), up to 3 decimals. */
export const measuredCountSchema = z.object({
    countedQuantity: z.string().trim().regex(DECIMAL_3, 'Enter the counted quantity, up to 3 decimals'),
})

export const auditNotesSchema = z.object({
    notes: z.string().trim().max(500, 'Keep the notes under 500 characters'),
})

export type AdjustmentFormValues = z.infer<typeof adjustmentSchema>
export type DamageFormValues = z.infer<typeof damageSchema>
export type ReceiptFormValues = z.infer<typeof receiptSchema>
export type ThresholdFormValues = z.infer<typeof thresholdSchema>
export type UnitCountFormValues = z.infer<typeof unitCountSchema>
export type MeasuredCountFormValues = z.infer<typeof measuredCountSchema>
export type AuditNotesFormValues = z.infer<typeof auditNotesSchema>

const blankToNull = (value: string): string | null => (value.trim() === '' ? null : value.trim())

export const toAdjustStockRequest = (values: AdjustmentFormValues): AdjustStockRequest => ({
    delta: Number(values.delta),
    reason: values.reason.trim(),
})

export const toDamageStockRequest = (values: DamageFormValues): DamageStockRequest => ({
    quantity: Number(values.quantity),
    reason: values.reason.trim(),
})

export const toReceiveStockRequest = (values: ReceiptFormValues): ReceiveStockRequest => ({
    quantity: Number(values.quantity),
    unitCost: values.unitCost.trim() === '' ? null : Number(values.unitCost),
    supplierName: blankToNull(values.supplierName),
    supplierReference: blankToNull(values.supplierReference),
    notes: blankToNull(values.notes),
})