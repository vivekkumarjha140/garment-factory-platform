import { Permission } from '../auth/permissions'

export interface NavItem {
    to: string
    label: string
    /** Shown only to signed-in people. */
    auth?: boolean
    /** Shown only to people holding this permission. Later phases add their screens here. */
    requires?: string
}

export const NAV_ITEMS: NavItem[] = [
    { to: '/', label: 'Home' },
    { to: '/shop', label: 'Shop' },
    { to: '/admin/users', label: 'Users', auth: true, requires: Permission.USER_VIEW },
    { to: '/account', label: 'Account', auth: true },
]

/** UX only: the backend checks the same permissions on every call. */
export function visibleNavItems(
    items: NavItem[],
    viewer: { authenticated: boolean; can: (permission: string) => boolean },
): NavItem[] {
    return items.filter((item) => (!item.auth || viewer.authenticated) && (!item.requires || viewer.can(item.requires)))
}