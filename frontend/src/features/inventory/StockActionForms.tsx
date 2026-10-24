import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { userMessage, type ApiError } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { TextField } from '../../components/ui/TextField'
import type { StockItem } from '../../types/inventory'
import { reportSubmitError } from './formHelpers'
import { inventoryKeys } from './queryKeys'
import { formatQuantity } from './quantity'
import {
    adjustmentSchema,
    damageSchema,
    receiptSchema,
    thresholdSchema,
    toAdjustStockRequest,
    toDamageStockRequest,
    toReceiveStockRequest,
    type AdjustmentFormValues,
    type DamageFormValues,
    type ReceiptFormValues,
    type ThresholdFormValues,
} from './schemas'
import { variantLabel } from './stockStatus'

export type StockAction = 'adjust' | 'receive' | 'damage' | 'threshold'

const TITLES: Record<StockAction, string> = {
    adjust: 'Adjust stock',
    receive: 'Receive stock',
    damage: 'Mark stock as damaged',
    threshold: 'Low-stock threshold',
}

interface FormProps {
    item: StockItem
    onClose: () => void
    onDone: (message: string) => void
}

interface ModalProps extends FormProps {
    action: StockAction
}

export function StockActionModal({ item, action, onClose, onDone }: ModalProps) {
    return (
        <Modal title={`${TITLES[action]}: ${variantLabel(item)}`} onClose={onClose}>
            <p className="mb-4 text-sm text-slate-600 dark:text-slate-400">
                On hand {formatQuantity(item.onHand)}, reserved {formatQuantity(item.reserved)}, available{' '}
                {formatQuantity(item.available)}.
            </p>
            {action === 'adjust' && <AdjustForm item={item} onClose={onClose} onDone={onDone} />}
            {action === 'receive' && <ReceiveForm item={item} onClose={onClose} onDone={onDone} />}
            {action === 'damage' && <DamageForm item={item} onClose={onClose} onDone={onDone} />}
            {action === 'threshold' && <ThresholdForm item={item} onClose={onClose} onDone={onDone} />}
        </Modal>
    )
}

function FormFooter({ submitLabel, submitting, onClose }: { submitLabel: string; submitting: boolean; onClose: () => void }) {
    return (
        <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={onClose}>
                Cancel
            </Button>
            <Button type="submit" loading={submitting}>
                {submitLabel}
            </Button>
        </div>
    )
}

const ADJUST_FIELDS = ['delta', 'reason'] as const

function AdjustForm({ item, onClose, onDone }: FormProps) {
    const queryClient = useQueryClient()
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<AdjustmentFormValues>({ resolver: zodResolver(adjustmentSchema), defaultValues: { delta: '', reason: '' } })

    async function onSubmit(values: AdjustmentFormValues) {
        setError(null)
        try {
            await inventoryApi.adjustStock(item.variantId, toAdjustStockRequest(values))
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
                inputMode="numeric"
                autoComplete="off"
                autoFocus
                hint="Use a negative number to remove stock, like -3."
                error={errors.delta?.message}
                {...register('delta')}
            />
            <TextField label="Reason" autoComplete="off" error={errors.reason?.message} {...register('reason')} />
            <FormFooter submitLabel="Save adjustment" submitting={isSubmitting} onClose={onClose} />
        </form>
    )
}

const RECEIVE_FIELDS = ['quantity', 'unitCost', 'supplierName', 'supplierReference', 'notes'] as const

function ReceiveForm({ item, onClose, onDone }: FormProps) {
    const queryClient = useQueryClient()
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<ReceiptFormValues>({
        resolver: zodResolver(receiptSchema),
        defaultValues: { quantity: '', unitCost: '', supplierName: '', supplierReference: '', notes: '' },
    })

    async function onSubmit(values: ReceiptFormValues) {
        setError(null)
        try {
            await inventoryApi.receiveStock(item.variantId, toReceiveStockRequest(values))
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
                inputMode="numeric"
                autoComplete="off"
                autoFocus
                error={errors.quantity?.message}
                {...register('quantity')}
            />
            <TextField
                label="Unit cost (optional)"
                inputMode="decimal"
                autoComplete="off"
                error={errors.unitCost?.message}
                {...register('unitCost')}
            />
            <TextField label="Supplier (optional)" autoComplete="off" error={errors.supplierName?.message} {...register('supplierName')} />
            <TextField
                label="Supplier reference (optional)"
                autoComplete="off"
                error={errors.supplierReference?.message}
                {...register('supplierReference')}
            />
            <TextField label="Notes (optional)" autoComplete="off" error={errors.notes?.message} {...register('notes')} />
            <FormFooter submitLabel="Record receipt" submitting={isSubmitting} onClose={onClose} />
        </form>
    )
}

const DAMAGE_FIELDS = ['quantity', 'reason'] as const

function DamageForm({ item, onClose, onDone }: FormProps) {
    const queryClient = useQueryClient()
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<DamageFormValues>({ resolver: zodResolver(damageSchema), defaultValues: { quantity: '', reason: '' } })

    async function onSubmit(values: DamageFormValues) {
        setError(null)
        try {
            await inventoryApi.damageStock(item.variantId, toDamageStockRequest(values))
            await queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
            onDone('Stock marked as damaged.')
        } catch (caught) {
            reportSubmitError(caught, setFieldError, DAMAGE_FIELDS, setError)
        }
    }

    return (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            {error && <Alert variant="error">{userMessage(error)}</Alert>}
            <TextField
                label="Damaged quantity"
                inputMode="numeric"
                autoComplete="off"
                autoFocus
                hint="Only stock that is not reserved for an order can be marked damaged."
                error={errors.quantity?.message}
                {...register('quantity')}
            />
            <TextField label="Reason" autoComplete="off" error={errors.reason?.message} {...register('reason')} />
            <FormFooter submitLabel="Mark as damaged" submitting={isSubmitting} onClose={onClose} />
        </form>
    )
}

const THRESHOLD_FIELDS = ['lowStockThreshold'] as const

function ThresholdForm({ item, onClose, onDone }: FormProps) {
    const queryClient = useQueryClient()
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<ThresholdFormValues>({
        resolver: zodResolver(thresholdSchema),
        defaultValues: { lowStockThreshold: String(item.lowStockThreshold) },
    })

    async function onSubmit(values: ThresholdFormValues) {
        setError(null)
        try {
            await inventoryApi.updateThreshold(item.variantId, Number(values.lowStockThreshold))
            await queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
            onDone('Threshold updated.')
        } catch (caught) {
            reportSubmitError(caught, setFieldError, THRESHOLD_FIELDS, setError)
        }
    }

    return (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
            {error && <Alert variant="error">{userMessage(error)}</Alert>}
            <TextField
                label="Alert when available stock is at or below"
                inputMode="numeric"
                autoComplete="off"
                autoFocus
                error={errors.lowStockThreshold?.message}
                {...register('lowStockThreshold')}
            />
            <FormFooter submitLabel="Save threshold" submitting={isSubmitting} onClose={onClose} />
        </form>
    )
}