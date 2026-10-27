import { useState, type FormEvent } from 'react'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/TextField'
import type { CartCoupon } from '../../types/cart'

interface CouponFormProps {
    coupon: CartCoupon | null
    /** Why a coupon that is still stored on the cart no longer applies. */
    storedCouponError: string | null
    /** The server's answer to the last code that was tried. */
    applyError: string | null
    busy: boolean
    onApply: (code: string) => void
    onRemove: () => void
}

export function CouponForm({ coupon, storedCouponError, applyError, busy, onApply, onRemove }: CouponFormProps) {
    const [code, setCode] = useState('')

    if (coupon) {
        return (
            <div className="flex items-center justify-between gap-3 rounded-lg border border-green-300 bg-green-50 p-3 text-sm text-green-900 dark:border-green-900 dark:bg-green-950 dark:text-green-200">
                <div>
                    <p className="font-medium">Coupon {coupon.code} applied</p>
                    {coupon.freeShipping && <p>Free shipping</p>}
                    {coupon.description && <p>{coupon.description}</p>}
                </div>
                <Button variant="secondary" disabled={busy} onClick={onRemove}>
                    Remove coupon
                </Button>
            </div>
        )
    }

    function handleSubmit(event: FormEvent) {
        event.preventDefault()
        const trimmed = code.trim()
        if (trimmed) onApply(trimmed)
    }

    return (
        <div className="space-y-2">
            {storedCouponError && (
                <Alert variant="info">
                    {storedCouponError}{' '}
                    <button type="button" className="underline" disabled={busy} onClick={onRemove}>
                        Remove coupon
                    </button>
                </Alert>
            )}
            <form onSubmit={handleSubmit} noValidate className="flex items-end gap-2">
                <div className="flex-1">
                    <TextField
                        label="Coupon code"
                        value={code}
                        onChange={(event) => setCode(event.target.value)}
                        autoComplete="off"
                        maxLength={40}
                        error={applyError ?? undefined}
                    />
                </div>
                <Button type="submit" variant="secondary" disabled={busy || code.trim() === ''}>
                    Apply
                </Button>
            </form>
        </div>
    )
}