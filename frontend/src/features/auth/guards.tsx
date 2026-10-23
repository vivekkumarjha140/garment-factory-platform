import { Link, Navigate, Outlet, useLocation } from 'react-router'
import { useAppSelector } from '../../app/hooks'
import { FullPageSpinner } from '../../components/ui/Spinner'
import { usePermissions } from './usePermissions'

/** Signed-in users only. While the session is being restored nothing is shown, so there is no flash of the login page. */
export function RequireAuth() {
    const status = useAppSelector((state) => state.auth.status)
    const location = useLocation()
    if (status === 'unknown') return <FullPageSpinner />
    if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location }} />
    return <Outlet />
}

/** Sign-in and sign-up pages: signed-in users are sent on to where they were heading, or home. */
export function RequireGuest() {
    const status = useAppSelector((state) => state.auth.status)
    const location = useLocation()
    if (status === 'unknown') return <FullPageSpinner />
    if (status === 'authenticated') {
        const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname
        return <Navigate to={from ?? '/'} replace />
    }
    return <Outlet />
}

/** UX only. The backend enforces the same permission on every request. */
export function RequirePermission({ permission }: { permission: string }) {
    const { can } = usePermissions()
    return can(permission) ? <Outlet /> : <AccessDenied />
}

export function AccessDenied() {
    return (
        <div role="alert" className="space-y-2 py-10 text-center">
            <h1 className="text-xl font-semibold">Access denied</h1>
            <p className="text-sm text-slate-600 dark:text-slate-400">You do not have permission to view this page.</p>
            <Link to="/" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                Back to home
            </Link>
        </div>
    )
}