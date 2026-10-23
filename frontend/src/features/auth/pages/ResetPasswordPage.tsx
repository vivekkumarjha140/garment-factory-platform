import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router'
import { authApi } from '../../../api/auth'
import { toApiError, userMessage, type ApiError } from '../../../api/errors'
import { Alert } from '../../../components/ui/Alert'
import { AuthCard } from '../../../components/ui/AuthCard'
import { Button } from '../../../components/ui/Button'
import { TextField } from '../../../components/ui/TextField'
import { resetPasswordSchema, type ResetPasswordForm } from '../schemas'

export function ResetPasswordPage() {
    const [params] = useSearchParams()
    const token = params.get('token')
    const [done, setDone] = useState(false)
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<ResetPasswordForm>({
        resolver: zodResolver(resetPasswordSchema),
        defaultValues: { newPassword: '', confirmPassword: '' },
    })

    async function onSubmit({ newPassword }: ResetPasswordForm) {
        if (!token) return
        setError(null)
        try {
            await authApi.resetPassword(token, newPassword)
            setDone(true)
        } catch (caught) {
            const apiError = toApiError(caught)
            if (apiError.code === 'WEAK_PASSWORD') {
                setFieldError('newPassword', { type: 'server', message: apiError.message })
            } else {
                setError(apiError)
            }
        }
    }

    const footer = (
        <Link to="/login" className="text-indigo-600 underline dark:text-indigo-400">
            Back to sign in
        </Link>
    )

    if (!token) {
        return (
            <AuthCard title="Choose a new password" footer={footer}>
                <Alert variant="error">This link is incomplete. Request a new reset link.</Alert>
                <Link to="/forgot-password" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                    Request a new link
                </Link>
            </AuthCard>
        )
    }

    if (done) {
        return (
            <AuthCard title="Password updated" footer={footer}>
                <Alert variant="success">Your password was changed and you were signed out everywhere. You can sign in now.</Alert>
            </AuthCard>
        )
    }

    return (
        <AuthCard title="Choose a new password" footer={footer}>
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
                {error && (
                    <Alert variant="error">
                        {userMessage(error)}{' '}
                        {error.code === 'INVALID_OR_EXPIRED_TOKEN' && (
                            <Link to="/forgot-password" className="underline">
                                Request a new link
                            </Link>
                        )}
                    </Alert>
                )}
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
                <Button type="submit" loading={isSubmitting} className="w-full">
                    Update password
                </Button>
            </form>
        </AuthCard>
    )
}