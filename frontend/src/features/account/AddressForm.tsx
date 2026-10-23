import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { customerApi } from '../../api/customer'
import { toApiError, userMessage, type ApiError } from '../../api/errors'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { SelectField } from '../../components/ui/SelectField'
import { TextField } from '../../components/ui/TextField'
import type { Address, AddressBody } from '../../types/customer'
import { applyFieldErrors } from '../auth/formErrors'
import { addressSchema, type AddressFormValues } from './schemas'

interface AddressFormProps {
    /** Present when editing; absent when adding a new address. */
    address?: Address
    /** True when the customer has no addresses yet: the first one is always the default. */
    isFirst: boolean
    /** Undefined while the list of states is still loading. */
    states: string[] | undefined
    onSaved: () => void
    onCancel: () => void
}

const FIELDS = ['label', 'recipientName', 'phone', 'line1', 'line2', 'city', 'state', 'pincode'] as const

function initialValues(address: Address | undefined, isFirst: boolean): AddressFormValues {
    if (address) {
        return {
            label: address.label ?? '',
            recipientName: address.recipientName,
            phone: address.phone,
            line1: address.line1,
            line2: address.line2 ?? '',
            city: address.city,
            state: address.state,
            pincode: address.pincode,
            defaultShipping: address.defaultShipping,
            defaultBilling: address.defaultBilling,
        }
    }
    return {
        label: '',
        recipientName: '',
        phone: '',
        line1: '',
        line2: '',
        city: '',
        state: '',
        pincode: '',
        defaultShipping: isFirst,
        defaultBilling: isFirst,
    }
}

export function AddressForm({ address, isFirst, states, onSaved, onCancel }: AddressFormProps) {
    const [error, setError] = useState<ApiError | null>(null)
    // A default cannot be switched off here: make another address the default instead.
    const shippingLocked = isFirst || address?.defaultShipping === true
    const billingLocked = isFirst || address?.defaultBilling === true

    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<AddressFormValues>({ resolver: zodResolver(addressSchema), defaultValues: initialValues(address, isFirst) })

    async function onSubmit(values: AddressFormValues) {
        setError(null)
        const body: AddressBody = {
            label: values.label || null,
            recipientName: values.recipientName,
            phone: values.phone,
            line1: values.line1,
            line2: values.line2 || null,
            city: values.city,
            state: values.state,
            pincode: values.pincode,
            defaultShipping: shippingLocked || values.defaultShipping,
            defaultBilling: billingLocked || values.defaultBilling,
        }
        try {
            if (address) {
                await customerApi.updateAddress(address.id, body)
            } else {
                await customerApi.createAddress(body)
            }
            onSaved()
        } catch (caught) {
            const apiError = toApiError(caught)
            if (apiError.code === 'INVALID_STATE') {
                setFieldError('state', { type: 'server', message: apiError.message })
            } else if (Object.keys(apiError.fieldErrors).length > 0) {
                applyFieldErrors(apiError.fieldErrors, setFieldError, FIELDS)
            } else {
                setError(apiError)
            }
        }
    }

    const title = address ? 'Edit address' : 'New address'

    return (
        <form
            onSubmit={handleSubmit(onSubmit)}
            noValidate
            aria-labelledby="address-form-title"
            className="max-w-xl space-y-4 rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900"
        >
            <h2 id="address-form-title" className="text-lg font-semibold">
                {title}
            </h2>
            {error && <Alert variant="error">{userMessage(error)}</Alert>}

            <TextField
                label="Label (optional)"
                hint="For example Home or Office"
                error={errors.label?.message}
                {...register('label')}
            />
            <TextField label="Recipient name" autoComplete="name" error={errors.recipientName?.message} {...register('recipientName')} />
            <TextField label="Phone number" type="tel" autoComplete="tel" error={errors.phone?.message} {...register('phone')} />
            <TextField
                label="House, flat or street"
                autoComplete="address-line1"
                error={errors.line1?.message}
                {...register('line1')}
            />
            <TextField
                label="Area or landmark (optional)"
                autoComplete="address-line2"
                error={errors.line2?.message}
                {...register('line2')}
            />
            <div className="grid gap-4 sm:grid-cols-2">
                <TextField label="City" autoComplete="address-level2" error={errors.city?.message} {...register('city')} />
                <TextField
                    label="Pincode"
                    inputMode="numeric"
                    autoComplete="postal-code"
                    maxLength={6}
                    error={errors.pincode?.message}
                    {...register('pincode')}
                />
            </div>
            <SelectField
                label="State or union territory"
                disabled={states === undefined}
                hint={states === undefined ? 'Loading the list of states…' : undefined}
                error={errors.state?.message}
                {...register('state')}
            >
                <option value="">Choose…</option>
                {(states ?? []).map((state) => (
                    <option key={state} value={state}>
                        {state}
                    </option>
                ))}
            </SelectField>

            <div className="space-y-2">
                <Checkbox
                    label="Use as my default shipping address"
                    disabled={shippingLocked}
                    hint={shippingLocked ? 'This is your default. Make another address the default to change it.' : undefined}
                    {...register('defaultShipping')}
                />
                <Checkbox
                    label="Use as my default billing address"
                    disabled={billingLocked}
                    hint={billingLocked ? 'This is your default. Make another address the default to change it.' : undefined}
                    {...register('defaultBilling')}
                />
            </div>

            <div className="flex gap-2">
                <Button type="submit" loading={isSubmitting}>
                    Save address
                </Button>
                <Button variant="secondary" onClick={onCancel}>
                    Cancel
                </Button>
            </div>
        </form>
    )
}