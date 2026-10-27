import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Button } from '../../components/ui/Button'
import { formatPrice } from '../../lib/format'
import type { Cart } from '../../types/cart'
import { deliveryEstimate } from './cartLogic'

function Row({ label, value, good = false }: { label: string; value: string; good?: boolean }) {
    return (
        <div className="flex justify-between gap-4">
            <dt className="text-slate-600 dark:text-slate-400">{label}</dt>
            <dd className={good ? 'font-medium text-green-700 dark:text-green-400' : ''}>{value}</dd>
        </div>
    )
}

function shippingText(cart: Cart): string {
    const { totals } = cart
    if (totals.shippingCalculated && totals.shippingCharge !== null) {
        return totals.shippingCharge === 0 ? 'Free' : formatPrice(totals.shippingCharge)
    }
    return cart.shippingError ?? 'Add a delivery address'
}

/** The button on the cart page: a link once checkout is possible, a disabled button until then. */
function ProceedToCheckout({ cart }: { cart: Cart }) {
    if (cart.checkoutReady && cart.totals.shippingCalculated) {
        return (
            <Link
                to="/checkout"
                className="inline-flex w-full items-center justify-center rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
            >
                Proceed to checkout
            </Link>
        )
    }
    return (
        <Button disabled className="w-full">
            Proceed to checkout
        </Button>
    )
}

/** `action` replaces the default "Proceed to checkout" button; the checkout page passes its own "Place order". */
export function CartSummary({ cart, action }: { cart: Cart; action?: ReactNode }) {
    const { totals, coupon } = cart
    const estimate = deliveryEstimate(cart)
    const itemWord = totals.totalQuantity === 1 ? 'item' : 'items'

    return (
        <section aria-label="Order summary" className="space-y-3 rounded-lg border border-slate-200 p-4 dark:border-slate-800">
            <h2 className="text-lg font-semibold">Order summary</h2>
            <dl className="space-y-2 text-sm">
                <Row label={`Subtotal (${totals.totalQuantity} ${itemWord})`} value={formatPrice(totals.subtotal)} />
                {totals.discount > 0 && (
                    <Row label={`Discount${coupon ? ` (${coupon.code})` : ''}`} value={`−${formatPrice(totals.discount)}`} good />
                )}
                <Row label="GST" value={formatPrice(totals.gstAmount)} />
                <Row label="Shipping" value={shippingText(cart)} />
            </dl>
            <div className="flex items-baseline justify-between gap-4 border-t border-slate-200 pt-3 dark:border-slate-800">
                <span className="font-semibold">{totals.shippingCalculated ? 'Total' : 'Total (excl. shipping)'}</span>
                <span data-testid="total" className="text-lg font-semibold">
                    {formatPrice(totals.total)}
                </span>
            </div>
            {estimate && (
                <p className="text-sm text-slate-600 dark:text-slate-400">
                    Estimated delivery: {estimate}
                    {cart.shipping ? ` · ${cart.shipping.zoneName}` : ''}
                </p>
            )}
            {action === undefined && !cart.checkoutReady && cart.lines.length > 0 && (
                <p className="text-sm text-red-700 dark:text-red-400">Fix the items marked above to continue.</p>
            )}
            {action ?? <ProceedToCheckout cart={cart} />}
        </section>
    )
}