import { NavLink } from 'react-router'
import { Permission } from '../auth/permissions'
import { usePermissions } from '../auth/usePermissions'

const INVENTORY_TABS = [
    { to: '/admin/inventory', label: 'Finished goods', end: true, requires: Permission.STOCK_VIEW },
    { to: '/admin/inventory/raw-materials', label: 'Raw materials', end: false, requires: Permission.STOCK_VIEW },
    { to: '/admin/inventory/audits', label: 'Stock audits', end: false, requires: Permission.INVENTORY_AUDIT },
]

/** UX only: the backend checks the same permissions on every call. */
export function InventoryTabs() {
    const { can } = usePermissions()
    const tabs = INVENTORY_TABS.filter((tab) => can(tab.requires))
    return (
        <nav aria-label="Inventory sections" className="flex flex-wrap gap-5 border-b border-slate-200 text-sm dark:border-slate-800">
            {tabs.map((tab) => (
                <NavLink
                    key={tab.to}
                    to={tab.to}
                    end={tab.end}
                    className={({ isActive }) =>
                        `-mb-px border-b-2 px-1 pb-2 ${
                            isActive
                                ? 'border-indigo-600 font-semibold text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                                : 'border-transparent text-slate-600 hover:underline dark:text-slate-300'
                        }`
                    }
                >
                    {tab.label}
                </NavLink>
            ))}
        </nav>
    )
}