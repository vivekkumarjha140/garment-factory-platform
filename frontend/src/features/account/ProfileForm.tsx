import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { customerApi } from '../../api/customer'
import { toApiError, userMessage, type ApiError } from '../../api/errors'
import { useAppDispatch } from '../../app/hooks'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { TextField } from '../../components/ui/TextField'
import { applyFieldErrors } from '../auth/formErrors'
import { profileNameChanged } from '../auth/authSlice'
import { PROFILE_KEY } from './queryKeys'
import { profileSchema, type ProfileFormValues } from './schemas'

const EMPTY: ProfileFormValues = { fullName: '', phone: '', marketingOptIn: false }
const FIELDS = ['fullName', 'phone'] as const

export function ProfileForm() {
    const queryClient = useQueryClient()
    const dispatch = useAppDispatch()
    const profile = useQuery({ queryKey: PROFILE_KEY, queryFn: () => customerApi.profile() })
    const [saved, setSaved] = useState(false)
    const [error, setError] = useState<ApiError | null>(null)

    // The form shows whatever the server holds, and resets itself when that changes.
    const values = useMemo<ProfileFormValues | undefined>(
        () =>
            profile.data
                ? { fullName: profile.data.fullName, phone: profile.data.phone ?? '', marketingOptIn: profile.data.marketingOptIn }
                : undefined,
        [profile.data],
    )

    const {
        register,
        handleSubmit,
        setError: setFieldError,
        formState: { errors, isSubmitting },
    } = useForm<ProfileFormValues>({ resolver: zodResolver(profileSchema), defaultValues: EMPTY, values })

    async function onSubmit(input: ProfileFormValues) {
        setError(null)
        setSaved(false)
        try {
            // An empty phone clears it; the other fields are always sent.
            const updated = await customerApi.updateProfile({
                fullName: input.fullName,
                phone: input.phone,
                marketingOptIn: input.marketingOptIn,
            })
            queryClient.setQueryData(PROFILE_KEY, updated)
            dispatch(profileNameChanged(updated.fullName))
            setSaved(true)
        } catch (caught) {
            const apiError = toApiError(caught)
            if (Object.keys(apiError.fieldErrors).length > 0) {
                applyFieldErrors(apiError.fieldErrors, setFieldError, FIELDS)
            } else {
                setError(apiError)
            }
        }
    }

    return (
        <section aria-labelledby="profile-title" className="space-y-4">
            <h2 id="profile-title" className="text-lg font-semibold">
                Your details
            </h2>

            {profile.isPending && (
                <div role="status" aria-label="Loading your details" className="max-w-md space-y-3">
                    <div className="h-10 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                    <div className="h-10 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                </div>
            )}

            {profile.isError && (
                <Alert variant="error">
                    {userMessage(toApiError(profile.error))}{' '}
                    <button type="button" className="underline" onClick={() => void profile.refetch()}>
                        Try again
                    </button>
                </Alert>
            )}

            {profile.data && (
                <form onSubmit={handleSubmit(onSubmit)} noValidate className="max-w-md space-y-4">
                    {error && <Alert variant="error">{userMessage(error)}</Alert>}
                    {saved && <Alert variant="success">Your details were saved.</Alert>}
                    <TextField
                        label="Email"
                        value={profile.data.email}
                        readOnly
                        hint="Your email address cannot be changed here."
                    />
                    <TextField label="Full name" autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
                    <TextField
                        label="Phone number"
                        type="tel"
                        autoComplete="tel"
                        hint="Optional. Start with + for an international number."
                        error={errors.phone?.message}
                        {...register('phone')}
                    />
                    <Checkbox label="Send me offers and updates" {...register('marketingOptIn')} />
                    <Button type="submit" loading={isSubmitting}>
                        Save changes
                    </Button>
                </form>
            )}
        </section>
    )
}