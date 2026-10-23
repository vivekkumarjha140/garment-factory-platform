import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { authApi } from '../../api/auth'
import { toApiError, userMessage, type ApiError } from '../../api/errors'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/TextField'
import { changePasswordSchema, type ChangePasswordForm as ChangePasswordValues } from '../auth/schemas'

const EMPTY: ChangePasswordValues = { currentPassword: '', newPassword: '', confirmPassword: '' }

export function ChangePasswordForm() {
    const [changed, setChanged] = useState(false)
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        reset,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<ChangePasswordValues>({ resolver: zodResolver(changePasswordSchema), defaultValues: EMPTY })

    async function onSubmit(values: ChangePasswordValues) {
        setError(null)
        setChanged(false)
        try {
            await authApi.changePassword(values.currentPassword, values.newPassword)
            reset(EMPTY)
            setChanged(true)
        } catch (caught) {
            const apiError = toApiError(caught)
            if (apiError.code === 'INVALID_CURRENT_PASSWORD') {
                setFieldError('currentPassword', { type: 'server', message: apiError.message })
            } else if (apiError.code === 'WEAK_PASSWORD' || apiError.code === 'PASSWORD_UNCHANGED') {
                setFieldError('newPassword', { type: 'server', message: apiError.message })
            } else {
                setError(apiError)
            }
        }
    }

    return (
        <section aria-labelledby="change-password-title" className="space-y-4">
            <h2 id="change-password-title" className="text-lg font-semibold">
                Change password
            </h2>
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="max-w-md space-y-4">
                {error && <Alert variant="error">{userMessage(error)}</Alert>}
                {changed && <Alert variant="success">Password changed. Your other devices were signed out.</Alert>}
                <TextField
                    label="Current password"
                    type="password"
                    autoComplete="current-password"
                    error={errors.currentPassword?.message}
                    {...register('currentPassword')}
                />
                <TextField
                    label="New password"
                    type="password"
                    autoComplete="new-password"
                    hint="10 to 64 characters with at least one letter and one digit"
                    error={errors.newPassword?.message}
                    {...register('newPassword')}
                />
                <TextField
                    label="Confirm new password"
                    type="password"
                    autoComplete="new-password"
                    error={errors.confirmPassword?.message}
                    {...register('confirmPassword')}
                />
                <Button type="submit" loading={isSubmitting}>
                    Change password
                </Button>
            </form>
        </section>
    )
}