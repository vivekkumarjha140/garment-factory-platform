import { NavLink } from 'react-router'

const LINK = 'border-b-2 px-1 pb-2 text-sm'

export function AccountTabs() {
    return (
        <nav aria-label="Account sections" className="flex gap-5 border-b border-slate-200 dark:border-slate-800">
            {[
                { to: '/account', label: 'Profile', end: true },
                { to: '/account/addresses', label: 'Addresses', end: false },
                { to: '/account/orders', label: 'Orders', end: false },
            ].map((tab) => (
                <NavLink
                    key={tab.to}
                    to={tab.to}
                    end={tab.end}
                    className={({ isActive }) =>
                        `${LINK} ${isActive ? 'border-indigo-600 font-semibold text-indigo-600 dark:border-indigo-400 dark:text-indigo-400' : 'border-transparent text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-white'}`
                    }
                >
                    {tab.label}
                </NavLink>
            ))}
        </nav>
    )
}