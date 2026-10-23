import { Link } from 'react-router'
import { useAppSelector } from '../../app/hooks'
import { usePermissions } from '../auth/usePermissions'
import { NAV_ITEMS, visibleNavItems } from './navItems'

export function HomePage() {
    const status = useAppSelector((state) => state.auth.status)
    const { user, can } = usePermissions()
    const links = visibleNavItems(NAV_ITEMS, { authenticated: status === 'authenticated', can }).filter((item) => item.to !== '/')

    return (
        <div className="space-y-4">
            <h1 className="text-2xl font-semibold">{user ? `Welcome, ${user.fullName}` : 'Garment Factory'}</h1>
            <p className="max-w-prose text-sm text-slate-600 dark:text-slate-400">
                T-shirts, lowers and other garments, made and printed in our own factory.
            </p>
            {user && <p className="text-sm text-slate-600 dark:text-slate-400">Signed in as {user.roles.join(', ')}.</p>}
            <ul className="flex flex-wrap gap-3">
                {links.map((item) => (
                    <li key={item.to}>
                        <Link
                            to={item.to}
                            className="block rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm font-medium shadow-sm hover:border-indigo-400 dark:border-slate-800 dark:bg-slate-900"
                        >
                            {item.label}
                        </Link>
                    </li>
                ))}
            </ul>
        </div>
    )
}