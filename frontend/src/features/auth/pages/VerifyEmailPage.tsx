import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router'
import { authApi } from '../../../api/auth'
import { toApiError, userMessage, type ApiError } from '../../../api/errors'
import { Alert } from '../../../components/ui/Alert'
import { AuthCard } from '../../../components/ui/AuthCard'
import { Button } from '../../../components/ui/Button'
import { TextField } from '../../../components/ui/TextField'
import { emailFormSchema, type EmailForm } from '../schemas'

type Phase = 'verifying' | 'success' | 'failed' | 'missing'

function ResendForm() {
    const [sent, setSent] = useState(false)
    const [error, setError] = useState<ApiError | null>(null)
    const {
        register,
        handleSubmit,
        formState: { errors, isSubmitting },
    } = useForm<EmailForm>({ resolver: zodResolver(emailFormSchema), defaultValues: { email: '' } })

    async function onSubmit({ email }: EmailForm) {
        setError(null)
        try {
            await authApi.resendVerification(email)
            setSent(true)
        } catch (caught) {
            setError(toApiError(caught))
        }
    }

    if (sent) return <Alert variant="success">If the account is waiting for verification, a new email is on its way.</Alert>
    return (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-3">
            {error && <Alert variant="error">{userMessage(error)}</Alert>}
            <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
            <Button type="submit" loading={isSubmitting} className="w-full">
                Send a new link
            </Button>
        </form>
    )
}

export function VerifyEmailPage() {
    const [params] = useSearchParams()
    const token = params.get('token')
    const [phase, setPhase] = useState<Phase>(token ? 'verifying' : 'missing')
    // The link works once. StrictMode runs effects twice in development, so guard against a second call.
    const started = useRef(false)

    useEffect(() => {
        if (!token || started.current) return
        started.current = true
        authApi
            .verifyEmail(token)
            .then(() => setPhase('success'))
            .catch(() => setPhase('failed'))
    }, [token])

    return (
        <AuthCard
            title="Verify your email"
            footer={
                <Link to="/login" className="text-indigo-600 underline dark:text-indigo-400">
                    Go to sign in
                </Link>
            }
        >
            {phase === 'verifying' && (
                <p role="status" className="text-sm">
                    Verifying your email address…
                </p>
            )}
            {phase === 'success' && <Alert variant="success">Your email is verified. You can sign in now.</Alert>}
            {(phase === 'failed' || phase === 'missing') && (
                <>
                    <Alert variant="error">
                        {phase === 'missing' ? 'This link is incomplete.' : 'This link is invalid or has expired.'} Request a new one below.
                    </Alert>
                    <ResendForm />
                </>
            )}
        </AuthCard>
    )
}