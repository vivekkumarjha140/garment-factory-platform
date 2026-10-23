import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'
import { authApi } from '../../../api/auth'
import { toApiError, userMessage, type ApiError } from '../../../api/errors'
import { useAppDispatch } from '../../../app/hooks'
import { Alert } from '../../../components/ui/Alert'
import { AuthCard } from '../../../components/ui/AuthCard'
import { Button } from '../../../components/ui/Button'
import { TextField } from '../../../components/ui/TextField'
import { signIn } from '../authActions'
import { loginSchema, type LoginForm } from '../schemas'

export function LoginPage() {
    const dispatch = useAppDispatch()
    const navigate = useNavigate()
    const location = useLocation()
    const [error, setError] = useState<ApiError | null>(null)
    const [resent, setResent] = useState(false)
    const {
        register,
        handleSubmit,
        getValues,
        formState: { errors, isSubmitting },
    } = useForm<LoginForm>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } })

    const redirectTo = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/'

    async function onSubmit(values: LoginForm) {
        setError(null)
        setResent(false)
        try {
            await dispatch(signIn(values))
            navigate(redirectTo, { replace: true })
        } catch (caught) {
            setError(toApiError(caught))
        }
    }

    async function resendVerification() {
        try {
            await authApi.resendVerification(getValues('email'))
            setResent(true)
        } catch (caught) {
            setError(toApiError(caught))
        }
    }

    return (
        <AuthCard
            title="Sign in"
            footer={
                <>
                    New here?{' '}
                    <Link to="/register" className="text-indigo-600 underline dark:text-indigo-400">
                        Create an account
                    </Link>
                </>
            }
        >
            <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
                {error && <Alert variant="error">{userMessage(error)}</Alert>}
                {error?.code === 'EMAIL_NOT_VERIFIED' && !resent && (
                    <Button variant="secondary" onClick={() => void resendVerification()}>
                        Resend verification email
                    </Button>
                )}
                {resent && <Alert variant="success">If the account is waiting for verification, a new email is on its way.</Alert>}
                <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
                <TextField
                    label="Password"
                    type="password"
                    autoComplete="current-password"
                    error={errors.password?.message}
                    {...register('password')}
                />
                <Button type="submit" loading={isSubmitting} className="w-full">
                    Sign in
                </Button>
                <p className="text-center text-sm">
                    <Link to="/forgot-password" className="text-indigo-600 underline dark:text-indigo-400">
                        Forgot your password?
                    </Link>
                </p>
            </form>
        </AuthCard>
    )
}