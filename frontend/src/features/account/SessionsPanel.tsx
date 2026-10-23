import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { authApi } from '../../api/auth'
import { toApiError, userMessage } from '../../api/errors'
import { useAppDispatch } from '../../app/hooks'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { signOutEverywhere } from '../auth/authActions'
import { describeUserAgent } from '../auth/device'

const SESSIONS_KEY = ['auth', 'sessions']

export function SessionsPanel() {
    const queryClient = useQueryClient()
    const dispatch = useAppDispatch()
    const navigate = useNavigate()
    const [signOutError, setSignOutError] = useState<string | null>(null)

    const sessions = useQuery({ queryKey: SESSIONS_KEY, queryFn: authApi.listSessions })
    const revoke = useMutation({
        mutationFn: authApi.revokeSession,
        onSuccess: () => queryClient.invalidateQueries({ queryKey: SESSIONS_KEY }),
    })

    async function handleSignOutEverywhere() {
        setSignOutError(null)
        try {
            await dispatch(signOutEverywhere())
            navigate('/login', { replace: true })
        } catch (caught) {
            setSignOutError(userMessage(toApiError(caught)))
        }
    }

    return (
        <section aria-labelledby="sessions-title" className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 id="sessions-title" className="text-lg font-semibold">
                    Where you are signed in
                </h2>
                <Button variant="danger" onClick={() => void handleSignOutEverywhere()}>
                    Sign out everywhere
                </Button>
            </div>

            {signOutError && <Alert variant="error">{signOutError}</Alert>}
            {revoke.isError && <Alert variant="error">{userMessage(toApiError(revoke.error))}</Alert>}

            {sessions.isPending && (
                <div role="status" aria-label="Loading sessions" className="space-y-2">
                    <div className="h-12 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                    <div className="h-12 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                </div>
            )}

            {sessions.isError && (
                <Alert variant="error">
                    {userMessage(toApiError(sessions.error))}{' '}
                    <button type="button" className="underline" onClick={() => void sessions.refetch()}>
                        Retry
                    </button>
                </Alert>
            )}

            {sessions.data && sessions.data.length === 0 && <p className="text-sm text-slate-500">No active sessions.</p>}

            {sessions.data && sessions.data.length > 0 && (
                <ul className="divide-y divide-slate-200 rounded-md border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
                    {sessions.data.map((session) => (
                        <li key={session.id} className="flex flex-wrap items-center justify-between gap-3 p-3 text-sm">
                            <div>
                                <p className="font-medium">
                                    {describeUserAgent(session.userAgent)}
                                    {session.current && (
                                        <span className="ml-2 rounded bg-indigo-100 px-2 py-0.5 text-xs text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300">
                      This device
                    </span>
                                    )}
                                </p>
                                <p className="text-xs text-slate-500 dark:text-slate-400">
                                    {session.ip ?? 'Unknown IP'} · last active {new Date(session.lastActiveAt).toLocaleString()}
                                </p>
                            </div>
                            {!session.current && (
                                <Button
                                    variant="secondary"
                                    loading={revoke.isPending && revoke.variables === session.id}
                                    onClick={() => revoke.mutate(session.id)}
                                >
                                    Sign out
                                </Button>
                            )}
                        </li>
                    ))}
                </ul>
            )}
        </section>
    )
}