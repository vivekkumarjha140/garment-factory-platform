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
import { emailFormSchema, type EmailForm } from '../schemas'

export function ForgotPasswordPage() {
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
            await authApi.forgotPassword(email)
            setSent(true)
        } catch (caught) {
            setError(toApiError(caught))
        }
    }

    return (
        <AuthCard
            title="Forgot your password?"
            subtitle="We will email you a link to choose a new one."
            footer={
                <Link to="/login" className="text-indigo-600 underline dark:text-indigo-400">
                    Back to sign in
                </Link>
            }
        >
            {sent ? (
                <Alert variant="success">If an account exists for this email, a reset link has been sent. It is valid for 30 minutes.</Alert>
            ) : (
                <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
                    {error && <Alert variant="error">{userMessage(error)}</Alert>}
                    <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
                    <Button type="submit" loading={isSubmitting} className="w-full">
                        Send reset link
                    </Button>
                </form>
            )}
        </AuthCard>
    )
}