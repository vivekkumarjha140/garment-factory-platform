import { describe, expect, it } from 'vitest'
import { Permission } from '../auth/permissions'
import { NAV_ITEMS, visibleNavItems } from './navItems'

const labels = (authenticated: boolean, permissions: string[] = []) =>
    visibleNavItems(NAV_ITEMS, { authenticated, can: (permission) => permissions.includes(permission) }).map((item) => item.label)

describe('visibleNavItems', () => {
    it('shows only Home and Shop to someone who is signed out', () => {
        expect(labels(false)).toEqual(['Home', 'Shop'])
    })

    it('adds Account once signed in', () => {
        expect(labels(true)).toEqual(['Home', 'Shop', 'Account'])
    })

    it('adds staff screens only for people holding the permission', () => {
        expect(labels(true, [Permission.USER_VIEW])).toEqual(['Home', 'Shop', 'Users', 'Account'])
    })

    it('never shows a signed-in screen to a signed-out person, even with the permission', () => {
        expect(labels(false, [Permission.USER_VIEW])).toEqual(['Home', 'Shop'])
    })
})