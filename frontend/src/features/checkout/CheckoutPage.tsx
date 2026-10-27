import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { customerApi } from '../../api/customer'
import { toApiError, userMessage, type ApiError } from '../../api/errors'
import { ordersApi } from '../../api/orders'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { formatPrice } from '../../lib/format'
import { usePageTitle } from '../../lib/usePageTitle'
import type { Order, PaymentMethod } from '../../types/order'
import { ADDRESSES_KEY } from '../account/queryKeys'
import { AddressPicker } from '../cart/AddressPicker'
import { deliveryEstimate } from '../cart/cartLogic'
import { CartSummary } from '../cart/CartSummary'
import { CART_KEY, useCart } from '../cart/useCart'
import { orderKeys } from '../orders/queryKeys'
import { errorText } from './errorText'
import { createKeyHolder } from './idempotency'
import { payOnline } from './payOnline'

const METHODS: { value: PaymentMethod; label: string; hint: string }[] = [
    { value: 'ONLINE', label: 'Pay online', hint: 'UPI, cards, netbanking and wallets through Razorpay.' },
    {
        value: 'COD',
        label: 'Cash on delivery',
        hint: 'Pay when the order arrives. Available for smaller orders; we check when you place it.',
    },
]

function PaymentMethodChoice({ value, onChange, disabled }: { value: PaymentMethod; onChange: (v: PaymentMethod) => void; disabled: boolean }) {
    return (
        <fieldset className="space-y-2" disabled={disabled}>
    <legend className="text-sm font-medium">Payment method</legend>
    {METHODS.map((method) => (
        <label
            key={method.value}
        className="flex cursor-pointer items-start gap-3 rounded-md border border-slate-300 p-3 text-sm has-[:checked]:border-indigo-500 dark:border-slate-700"
        >
        <input
            type="radio"
        name="payment-method"
        value={method.value}
        checked={value === method.value}
        onChange={() => onChange(method.value)}
        className="mt-1"
        />
        <span>
            <span className="block font-medium">{method.label}</span>
            <span className="block text-slate-600 dark:text-slate-400">{method.hint}</span>
        </span>
        </label>
    ))}
    </fieldset>
)
}

export function CheckoutPage() {
    usePageTitle('Checkout')
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const [addressId, setAddressId] = useState<string | null>(null)
    const [method, setMethod] = useState<PaymentMethod>('ONLINE')
    const [placing, setPlacing] = useState(false)
    const [leaving, setLeaving] = useState(false)
    const [error, setError] = useState<ApiError | null>(null)
    const keys = useRef(createKeyHolder())

    const cart = useCart(addressId)
    const addresses = useQuery({ queryKey: ADDRESSES_KEY, queryFn: () => customerApi.addresses() })
    const data = cart.data
    const selectedAddressId = addressId ?? data?.shippingAddressId ?? null
    const canPlace = Boolean(data?.checkoutReady && data.totals.shippingCalculated && selectedAddressId && !placing)

    async function payAndRoute(orderNumber: string) {
        try {
            const outcome = await payOnline(orderNumber)
            if (outcome.kind === 'submitted') {
                navigate(`/orders/${orderNumber}/success`, { replace: true })
            } else {
                navigate(`/account/orders/${orderNumber}`, { replace: true, state: { notice: outcome } })
            }
        } catch (caught) {
            // The order exists and can be paid later from its page.
            navigate(`/account/orders/${orderNumber}`, { replace: true, state: { notice: { kind: 'failed', message: errorText(caught) } } })
        }
    }

    async function placeOrder() {
        if (!selectedAddressId) return
        setError(null)
        setPlacing(true)
        let order: Order
        try {
            order = await ordersApi.place(
                { shippingAddressId: selectedAddressId, billingAddressId: null, paymentMethod: method },
                keys.current.keyFor(`${selectedAddressId}|${method}`),
            )
        } catch (caught) {
            setError(toApiError(caught))
            setPlacing(false)
            void cart.refetch() // the cart or the stock may have changed under us
            return
        }
        keys.current.reset()
        setLeaving(true) // the cart is empty now; do not flash the empty-cart message
        queryClient.setQueryData(orderKeys.detail(order.orderNumber), order)
        void queryClient.invalidateQueries({ queryKey: CART_KEY })
        void queryClient.invalidateQueries({ queryKey: orderKeys.all })
        if (order.paymentMethod === 'COD') {
            navigate(`/orders/${order.orderNumber}/success`, { replace: true })
            return
        }
        await payAndRoute(order.orderNumber)
    }

    const estimate = data ? deliveryEstimate(data) : null
    const buttonLabel =
        method === 'COD'
            ? `Place order · pay ${formatPrice(data?.totals.total ?? 0)} on delivery`
            : `Place order & pay ${formatPrice(data?.totals.total ?? 0)}`

    return (
        <div className="space-y-6">
        <h1 className="text-2xl font-semibold">Checkout</h1>

    {cart.isPending && <div role="status" aria-label="Loading checkout" className="h-40 animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />}

    {cart.isError && !data && (
        <Alert variant="error">
            {userMessage(toApiError(cart.error))}{' '}
        <button type="button" className="underline" onClick={() => void cart.refetch()}>
        Try again
    </button>
    </Alert>
    )}

    {data && data.lines.length === 0 && !leaving && (
        <div className="space-y-3 rounded-lg border border-dashed border-slate-300 p-8 text-center dark:border-slate-700">
        <p className="font-medium">Your cart is empty.</p>
    <Link to="/shop" className="text-sm text-indigo-600 underline dark:text-indigo-400">
        Browse the shop
    </Link>
    </div>
    )}

    {data && data.lines.length > 0 && (
        <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-6">
        <section aria-label="Delivery" className="space-y-3">
    <h2 className="text-lg font-semibold">Delivery</h2>
        <AddressPicker
        addresses={addresses.data ?? []}
        selectedId={selectedAddressId}
        disabled={placing}
        onChange={setAddressId}
        />
        {data.shipping ? (
            <p className="text-sm text-slate-600 dark:text-slate-400">
                Shipping: {data.shipping.zoneName} · {estimate} ·{' '}
            {data.totals.shippingCharge === 0 ? 'Free' : formatPrice(data.totals.shippingCharge ?? 0)}
            </p>
        ) : (
            <p className="text-sm text-red-700 dark:text-red-400">{data.shippingError ?? 'Choose a delivery address.'}</p>
        )}
        </section>
        <PaymentMethodChoice value={method} onChange={setMethod} disabled={placing} />
    </div>

    <CartSummary
        cart={data}
        action={
            <div className="space-y-2">
    {error && <Alert variant="error">{userMessage(error)}</Alert>}
        {!data.checkoutReady && (
            <p className="text-sm text-red-700 dark:text-red-400">
                Some items need attention.{' '}
            <Link to="/cart" className="underline">
            Review your cart
        </Link>
        </p>
        )}
        <Button className="w-full" disabled={!canPlace} loading={placing} onClick={() => void placeOrder()}>
        {buttonLabel}
        </Button>
        </div>
    }
        />
        </div>
    )}
        </div>
    )
    }