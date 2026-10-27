import { NavLink } from 'react-router'
import { useAuthStatus } from '../auth/useAuthStatus'
import { badgeCount } from './cartLogic'
import { useCart } from './useCart'

/** Cart link with the number of items in it. Only for signed-in customers (the cart lives on the server). */
export function CartLink() {
    const status = useAuthStatus()
    const cart = useCart(null, status === 'authenticated')

    if (status !== 'authenticated') return null

    const count = badgeCount(cart.data)
    const label = count > 0 ? `Cart, ${count} ${count === 1 ? 'item' : 'items'}` : 'Cart'

    return (
        <NavLink
            to="/cart"
            aria-label={label}
            className={({ isActive }) =>
                `inline-flex items-center gap-1 ${isActive ? 'font-semibold text-indigo-600 dark:text-indigo-400' : 'text-slate-600 hover:underline dark:text-slate-300'}`
            }
        >
            Cart
            {count > 0 && (
                <span aria-hidden="true" className="rounded-full bg-indigo-600 px-1.5 text-xs font-medium text-white">
                    {count > 99 ? '99+' : count}
                </span>
            )}
        </NavLink>
    )
}