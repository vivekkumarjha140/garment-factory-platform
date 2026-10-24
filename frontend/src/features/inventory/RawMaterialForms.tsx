import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { userMessage, type ApiError } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { SelectField } from '../../components/ui/SelectField'
import { TextField } from '../../components/ui/TextField'
import type { RawMaterial } from '../../types/inventory'
import { reportSubmitError } from './formHelpers'
import { inventoryKeys } from './queryKeys'
import { formatQuantity } from './quantity'
import {
    RAW_MATERIAL_CATEGORIES,
    rawAdjustmentSchema,
    rawMaterialSchema,
    rawReceiptSchema,
    toAdjustRawMaterialRequest,
    toCreateRawMaterialRequest,
    toReceiveRawMaterialRequest,
    type RawAdjustmentFormValues,
    type RawMaterialFormValues,
    type RawReceiptFormValues,
} from './rawMaterialSchemas'

export type RawMaterialDialog = { kind: 'create' } | { kind: 'receive'; item: RawMaterial } | { kind: 'adjust'; item: RawMaterial }

interface Props {
    dialog: RawMaterialDialog
    onClose: () => void
    onDone: (message: string) => void
}

export function RawMaterialModal({ dialog, onClose, onDone }: Props) {
    if (dialog.kind === 'create') {
        return (
            <Modal title="Add raw material" onClose={onClose}>
                <CreateForm onClose={onClose} onDone={onDone} />
            </Modal>
        )
    }
    const { item } = dialog
    return (
        <Modal title={`${dialog.kind === 'receive' ? 'Receive stock' : 'Adjust stock'}: ${item.name}`} onClose={onClose}>
            <p className="mb-4 text-sm text-slate-600 dark:text-slate-400">
                On hand {formatQuantity(item.onHand)} {item.uom}, reserved {formatQuantity(item.reserved)}, available{' '}
                {formatQuantity(item.available)}.
            </p>
            {dialog.kind === 'receive' ? (
                <ReceiveForm item={item} onClose={onClose} onDone={onDone} />
            ) : (
                <AdjustForm item={item} onClose={onClose} onDone={onDone} />
            )}
        </Modal>
    )
}

function Footer({ label, submitting, onClose }: { label: string; submitting: boolean; onClose: () => void }) {
    return (
        <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={onClose}>
                Cancel
            </Button>
            <Button type="submit" loading={submitting}>
                {label}
            </Button>
        </div>
    )
}

const CREATE_FIELDS = ['code', 'name', 'category', 'uom', 'lowStockThreshold', 'unitCost'] as const

function CreateForm({ onClose, onDone }: Pick<Props, 'onClose' | 'onDone'>) {
    const queryClient = useQueryClient()
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<RawMaterialFormValues>({
        resolver: zodResolver(rawMaterialSchema),
        defaultValues: { code: '', name: '', category: '', uom: '', lowStockThreshold: '', unitCost: '' },
    })

    async function onSubmit(values: RawMaterialFormValues) {
        setError(null)
        try {
            await inventoryApi.createRawMaterial(toCreateRawMaterialRequest(values))
            await queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
            onDone('Raw material created.')
        } catch (caught) {
            reportSubmitError(caught, setFieldError, CREATE_FIELDS, setError)
        }
    }

    return (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            {error && <Alert variant="error">{userMessage(error)}</Alert>}
            <TextField label="Code" autoComplete="off" autoFocus hint="Unique, like FAB-JERSEY." error={errors.code?.message} {...register('code')} />
            <TextField label="Name" autoComplete="off" error={errors.name?.message} {...register('name')} />
            <SelectField label="Category" error={errors.category?.message} {...register('category')}>
                <option value="">Choose a category</option>
                {RAW_MATERIAL_CATEGORIES.map((entry) => (
                    <option key={entry.value} value={entry.value}>
                        {entry.label}
                    </option>
                ))}
            </SelectField>
            <TextField label="Unit of measure" autoComplete="off" hint="Like METER, KG or PIECE." error={errors.uom?.message} {...register('uom')} />
            <TextField
                label="Alert threshold (optional)"
                inputMode="decimal"
                autoComplete="off"
                error={errors.lowStockThreshold?.message}
                {...register('lowStockThreshold')}
            />
            <TextField label="Unit cost (optional)" inputMode="decimal" autoComplete="off" error={errors.unitCost?.message} {...register('unitCost')} />
            <Footer label="Create raw material" submitting={isSubmitting} onClose={onClose} />
        </form>
    )
}

const RECEIVE_FIELDS = ['quantity', 'unitCost', 'supplierName', 'supplierReference', 'notes'] as const

function ReceiveForm({ item, onClose, onDone }: Pick<Props, 'onClose' | 'onDone'> & { item: RawMaterial }) {
    const queryClient = useQueryClient()
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<RawReceiptFormValues>({
        resolver: zodResolver(rawReceiptSchema),
        defaultValues: { quantity: '', unitCost: '', supplierName: '', supplierReference: '', notes: '' },
    })

    async function onSubmit(values: RawReceiptFormValues) {
        setError(null)
        try {
            await inventoryApi.receiveRawMaterial(item.id, toReceiveRawMaterialRequest(values))
            await queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
            onDone('Receipt recorded.')
        } catch (caught) {
            reportSubmitError(caught, setFieldError, RECEIVE_FIELDS, setError)
        }
    }

    return (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            {error && <Alert variant="error">{userMessage(error)}</Alert>}
            <TextField
                label="Quantity received"
                inputMode="decimal"
                autoComplete="off"
                autoFocus
                hint={`In ${item.uom}.`}
                error={errors.quantity?.message}
                {...register('quantity')}
            />
            <TextField label="Unit cost (optional)" inputMode="decimal" autoComplete="off" error={errors.unitCost?.message} {...register('unitCost')} />
            <TextField label="Supplier (optional)" autoComplete="off" error={errors.supplierName?.message} {...register('supplierName')} />
            <TextField
                label="Supplier reference (optional)"
                autoComplete="off"
                error={errors.supplierReference?.message}
                {...register('supplierReference')}
            />
            <TextField label="Notes (optional)" autoComplete="off" error={errors.notes?.message} {...register('notes')} />
            <Footer label="Record receipt" submitting={isSubmitting} onClose={onClose} />
        </form>
    )
}

const ADJUST_FIELDS = ['delta', 'reason'] as const

function AdjustForm({ item, onClose, onDone }: Pick<Props, 'onClose' | 'onDone'> & { item: RawMaterial }) {
    const queryClient = useQueryClient()
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<RawAdjustmentFormValues>({ resolver: zodResolver(rawAdjustmentSchema), defaultValues: { delta: '', reason: '' } })

    async function onSubmit(values: RawAdjustmentFormValues) {
        setError(null)
        try {
            await inventoryApi.adjustRawMaterial(item.id, toAdjustRawMaterialRequest(values))
            await queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
            onDone('Stock adjusted.')
        } catch (caught) {
            reportSubmitError(caught, setFieldError, ADJUST_FIELDS, setError)
        }
    }

    return (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            {error && <Alert variant="error">{userMessage(error)}</Alert>}
            <TextField
                label="Change in quantity"
                inputMode="decimal"
                autoComplete="off"
                autoFocus
                hint={`In ${item.uom}. Use a negative number to remove stock.`}
                error={errors.delta?.message}
                {...register('delta')}
            />
            <TextField label="Reason" autoComplete="off" error={errors.reason?.message} {...register('reason')} />
            <Footer label="Save adjustment" submitting={isSubmitting} onClose={onClose} />
        </form>
    )
}