import { z } from 'zod'
import type {
    AdjustRawMaterialRequest,
    CreateRawMaterialRequest,
    RawMaterialCategory,
    ReceiveRawMaterialRequest,
} from '../../types/inventory'

export const RAW_MATERIAL_CATEGORIES: ReadonlyArray<{ value: RawMaterialCategory; label: string }> = [
    { value: 'FABRIC', label: 'Fabric' },
    { value: 'BLANK_GARMENT', label: 'Blank garment' },
    { value: 'CONSUMABLE', label: 'Consumable' },
    { value: 'PACKAGING', label: 'Packaging' },
]

export const categoryLabel = (category: RawMaterialCategory): string =>
    RAW_MATERIAL_CATEGORIES.find((entry) => entry.value === category)?.label ?? category

const MEASURE = /^\d+(\.\d{1,3})?$/
const SIGNED_MEASURE = /^[+-]?\d+(\.\d{1,3})?$/
const OPTIONAL_MEASURE = /^(\d+(\.\d{1,3})?)?$/
const MONEY = /^(\d+(\.\d{1,4})?)?$/
const MAX_QUANTITY = 1_000_000

const reason = z.string().trim().min(1, 'Give a reason').max(500, 'Keep the reason under 500 characters')
const optionalMoney = z.string().trim().regex(MONEY, 'Enter an amount like 250 or 250.50')

export const rawMaterialSchema = z.object({
    code: z
        .string()
        .trim()
        .min(1, 'Enter a code')
        .max(40, 'Keep the code under 40 characters')
        .regex(/^[A-Za-z0-9][A-Za-z0-9._-]*$/, 'Use letters, digits, dots, hyphens or underscores'),
    name: z.string().trim().min(1, 'Enter a name').max(150, 'Keep the name under 150 characters'),
    category: z.string().refine((value) => RAW_MATERIAL_CATEGORIES.some((entry) => entry.value === value), 'Choose a category'),
    uom: z.string().trim().min(1, 'Enter the unit, like METER or KG').max(20, 'Keep the unit under 20 characters'),
    lowStockThreshold: z.string().trim().regex(OPTIONAL_MEASURE, 'Enter a quantity, like 50 or 12.5'),
    unitCost: optionalMoney,
})

export const rawReceiptSchema = z.object({
    quantity: z
        .string()
        .trim()
        .regex(MEASURE, 'Enter a quantity, like 50 or 12.5')
        .refine((value) => !MEASURE.test(value) || Number(value) > 0, 'Enter more than 0')
        .refine((value) => !MEASURE.test(value) || Number(value) <= MAX_QUANTITY, 'That is more than the system accepts'),
    unitCost: optionalMoney,
    supplierName: z.string().trim().max(150, 'Keep the supplier name under 150 characters'),
    supplierReference: z.string().trim().max(100, 'Keep the reference under 100 characters'),
    notes: z.string().trim().max(500, 'Keep the notes under 500 characters'),
})

export const rawAdjustmentSchema = z.object({
    delta: z
        .string()
        .trim()
        .regex(SIGNED_MEASURE, 'Enter a quantity like 5, -3 or 2.5')
        .refine((value) => !SIGNED_MEASURE.test(value) || Number(value) !== 0, 'The change cannot be zero')
        .refine((value) => !SIGNED_MEASURE.test(value) || Math.abs(Number(value)) <= MAX_QUANTITY, 'That is more than the system accepts'),
    reason,
})

export type RawMaterialFormValues = z.infer<typeof rawMaterialSchema>
export type RawReceiptFormValues = z.infer<typeof rawReceiptSchema>
export type RawAdjustmentFormValues = z.infer<typeof rawAdjustmentSchema>

const blankToNull = (value: string): string | null => (value.trim() === '' ? null : value.trim())
const numberOrNull = (value: string): number | null => (value.trim() === '' ? null : Number(value))

export const toCreateRawMaterialRequest = (values: RawMaterialFormValues): CreateRawMaterialRequest => ({
    code: values.code.trim(),
    name: values.name.trim(),
    category: values.category as RawMaterialCategory,
    uom: values.uom.trim(),
    lowStockThreshold: numberOrNull(values.lowStockThreshold),
    unitCost: numberOrNull(values.unitCost),
})

export const toReceiveRawMaterialRequest = (values: RawReceiptFormValues): ReceiveRawMaterialRequest => ({
    quantity: Number(values.quantity),
    unitCost: numberOrNull(values.unitCost),
    supplierName: blankToNull(values.supplierName),
    supplierReference: blankToNull(values.supplierReference),
    notes: blankToNull(values.notes),
})

export const toAdjustRawMaterialRequest = (values: RawAdjustmentFormValues): AdjustRawMaterialRequest => ({
    delta: Number(values.delta),
    reason: values.reason.trim(),
})