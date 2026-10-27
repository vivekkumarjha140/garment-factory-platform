import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { Button } from '../../components/ui/Button'
import { signOut } from '../auth/authActions'
import { usePermissions } from '../auth/usePermissions'
import { CartLink } from '../cart/CartLink'
import { ThemeToggle } from '../ui/ThemeToggle'
import { NAV_ITEMS, visibleNavItems } from './navItems'

const LINK_BUTTON =
    'inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400'

/** Frame for every page. The shop is open to everyone, so it works signed out as well as signed in. */
export function AppLayout() {
    const dispatch = useAppDispatch()
    const navigate = useNavigate()
    const location = useLocation()
    const status = useAppSelector((state) => state.auth.status)
    const { user, can } = usePermissions()
    const items = visibleNavItems(NAV_ITEMS, { authenticated: status === 'authenticated', can })

    async function handleSignOut() {
        await dispatch(signOut())
        navigate('/login', { replace: true })
    }

    return (
        <div className="min-h-screen">
            <a
                href="#main"
                className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:rounded focus:bg-white focus:px-3 focus:py-2 focus:text-sm focus:shadow dark:focus:bg-slate-900"
            >
                Skip to content
            </a>
            <header className="border-b border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
                <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 p-4">
                    <nav aria-label="Main" className="flex flex-wrap items-center gap-4 text-sm">
                        <span className="font-semibold">Garment Factory</span>
                        {items.map((item) => (
                            <NavLink
                                key={item.to}
                                to={item.to}
                                end={item.to === '/'}
                                className={({ isActive }) =>
                                    isActive ? 'font-semibold text-indigo-600 dark:text-indigo-400' : 'text-slate-600 hover:underline dark:text-slate-300'
                                }
                            >
                                {item.label}
                            </NavLink>
                        ))}
                    </nav>
                    <div className="flex items-center gap-3 text-sm">
                        <CartLink />
                        <ThemeToggle />
                        {status === 'authenticated' && (
                            <>
                                <span className="hidden text-slate-500 sm:inline dark:text-slate-400">{user?.fullName}</span>
                                <Button variant="secondary" onClick={() => void handleSignOut()}>
                                    Sign out
                                </Button>
                            </>
                        )}
                        {status === 'anonymous' && (
                            <>
                                <Link
                                    to="/login"
                                    state={{ from: location }}
                                    className={`${LINK_BUTTON} border border-slate-300 text-slate-700 hover:bg-slate-100 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800`}
                                >
                                    Sign in
                                </Link>
                                <Link to="/register" className={`${LINK_BUTTON} bg-indigo-600 text-white hover:bg-indigo-700`}>
                                    Create account
                                </Link>
                            </>
                        )}
                    </div>
                </div>
            </header>
            <main id="main" className="mx-auto max-w-6xl p-6">
                <Outlet />
            </main>
        </div>
    )
}