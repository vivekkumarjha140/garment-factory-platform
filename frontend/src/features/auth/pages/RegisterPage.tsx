import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { authApi } from '../../../api/auth'
import { toApiError, userMessage, type ApiError } from '../../../api/errors'
import { Alert } from '../../../components/ui/Alert'
import { AuthCard } from '../../../components/ui/AuthCard'
import { Button } from '../../../components/ui/Button'
import { TextField } from '../../../components/ui/TextField'
import { applyFieldErrors } from '../formErrors'
import { registerSchema, type RegisterForm } from '../schemas'

export function RegisterPage() {
    const [done, setDone] = useState(false)
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<RegisterForm>({
        resolver: zodResolver(registerSchema),
        defaultValues: { fullName: '', email: '', phone: '', password: '', confirmPassword: '' },
    })

    async function onSubmit(values: RegisterForm) {
        setError(null)
        try {
            await authApi.register({
                fullName: values.fullName,
                email: values.email,
                password: values.password,
                phone: values.phone || undefined,
            })
            setDone(true)
        } catch (caught) {
            const apiError = toApiError(caught)
            applyFieldErrors(apiError.fieldErrors, setFieldError, ['fullName', 'email', 'phone', 'password'])
            if (apiError.code === 'WEAK_PASSWORD') {
                setFieldError('password', { type: 'server', message: apiError.message })
            } else if (Object.keys(apiError.fieldErrors).length === 0) {
                setError(apiError)
            }
        }
    }

    if (done) {
        return (
            <AuthCard
                title="Check your email"
                footer={
                    <Link to="/login" className="text-indigo-600 underline dark:text-indigo-400">
                        Back to sign in
                    </Link>
                }
            >
                <Alert variant="success">
                    If this email can be registered, we sent a verification link. It is valid for 24 hours.
                </Alert>
            </AuthCard>
        )
    }

    return (
        <AuthCard
            title="Create your account"
            footer={
                <>
                    Already registered?{' '}
                    <Link to="/login" className="text-indigo-600 underline dark:text-indigo-400">
                        Sign in
                    </Link>
                </>
            }
        >
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
                {error && <Alert variant="error">{userMessage(error)}</Alert>}
                <TextField label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
                <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
                <TextField
                    label="Phone (optional)"
                    type="tel"
                    autoComplete="tel"
                    error={errors.phone?.message}
                    {...register('phone')}
                />
                <TextField
                    label="Password"
                    type="password"
                    autoComplete="new-password"
                    hint="10 to 64 characters with at least one letter and one digit"
                    error={errors.password?.message}
                    {...register('password')}
                />
                <TextField
                    label="Confirm password"
                    type="password"
                    autoComplete="new-password"
                    error={errors.confirmPassword?.message}
                    {...register('confirmPassword')}
                />
                <Button type="submit" loading={isSubmitting} className="w-full">
                    Create account
                </Button>
            </form>
        </AuthCard>
    )
}