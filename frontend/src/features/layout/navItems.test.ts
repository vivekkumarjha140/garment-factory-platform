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

    it('shows Inventory to people who can view stock, without the audit screen', () => {
        expect(labels(true, [Permission.STOCK_VIEW])).toEqual(['Home', 'Shop', 'Inventory', 'Account'])
    })

    it('shows Stock audits only to people holding the audit permission', () => {
        expect(labels(true, [Permission.INVENTORY_AUDIT])).toEqual(['Home', 'Shop', 'Stock audits', 'Account'])
    })

    it('shows Orders to people who can view orders', () => {
        expect(labels(true, [Permission.ORDER_VIEW])).toEqual(['Home', 'Shop', 'Orders', 'Account'])
    })

    it('does not show Orders for the fulfil or cancel permission alone', () => {
        expect(labels(true, [Permission.ORDER_FULFIL, Permission.ORDER_CANCEL])).toEqual(['Home', 'Shop', 'Account'])
    })

    it('shows both inventory screens to a warehouse manager', () => {
        expect(labels(true, [Permission.STOCK_VIEW, Permission.STOCK_ADJUST, Permission.INVENTORY_AUDIT])).toEqual([
            'Home',
            'Shop',
            'Inventory',
            'Stock audits',
            'Account',
        ])
    })
})