import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { toApiError, userMessage } from '../../api/errors'
import { ordersApi } from '../../api/orders'
import { Alert } from '../../components/ui/Alert'
import { formatPrice } from '../../lib/format'
import { usePageTitle } from '../../lib/usePageTitle'
import { awaitingPayment, placementOutcome } from './orderLogic'
import { orderKeys } from './queryKeys'

interface Props {
    /** How often to ask the server while payment is unconfirmed, and for how long. Tests shorten both. */
    pollMs?: number
    maxWaitMs?: number
}

/**
 * Where checkout ends. It never assumes success because the customer arrived here: it asks the server, and while the
 * payment is still unconfirmed it keeps asking (the webhook can take a few seconds).
 */
export function OrderSuccessPage({ pollMs = 2000, maxWaitMs = 30_000 }: Props) {
    const { orderNumber = '' } = useParams()
    usePageTitle('Order status')
    const startedAt = useRef(Date.now())
    const [timedOut, setTimedOut] = useState(false)

    useEffect(() => {
        const timer = setTimeout(() => setTimedOut(true), maxWaitMs)
        return () => clearTimeout(timer)
    }, [maxWaitMs])

    const order = useQuery({
        queryKey: orderKeys.detail(orderNumber),
        queryFn: () => ordersApi.get(orderNumber),
        refetchInterval: (query) => {
            const data = query.state.data
            return data && awaitingPayment(data) && Date.now() - startedAt.current < maxWaitMs ? pollMs : false
        },
    })
    const data = order.data

    if (order.isPending) {
        return <p role="status" className="py-10 text-center text-sm text-slate-500">Checking your order…</p>
    }
    if (!data) {
        return (
            <div className="space-y-3">
                <Alert variant="error">{userMessage(toApiError(order.error))}</Alert>
                <Link to="/account/orders" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                    See your orders
                </Link>
            </div>
        )
    }

    const outcome = placementOutcome(data)
    const detailLink = `/account/orders/${data.orderNumber}`

    return (
        <div className="mx-auto max-w-xl space-y-5 py-6 text-center">
            {outcome === 'confirmed' && (
                <>
                    <h1 className="text-2xl font-semibold text-green-700 dark:text-green-400">Order confirmed</h1>
                    <p>Payment received. Thank you! We have emailed you the details.</p>
                </>
            )}
            {outcome === 'cod-confirmed' && (
                <>
                    <h1 className="text-2xl font-semibold text-green-700 dark:text-green-400">Order placed</h1>
                    <p>Please keep {formatPrice(data.grandTotal)} ready to pay when it arrives.</p>
                </>
            )}
            {outcome === 'awaiting' && (
                <>
                    <h1 className="text-2xl font-semibold">Confirming your payment…</h1>
                    <p role="status">
                        {timedOut
                            ? 'This is taking longer than usual. If money was deducted, your order will be confirmed automatically or refunded. Please do not pay again; check your order page.'
                            : 'This usually takes a few seconds. Please do not pay again or close this page.'}
                    </p>
                </>
            )}
            {outcome === 'failed' && (
                <>
                    <h1 className="text-2xl font-semibold text-red-700 dark:text-red-400">Payment failed</h1>
                    <p>Your order is saved. You can try paying again from your order page.</p>
                </>
            )}
            {outcome === 'cancelled' && (
                <>
                    <h1 className="text-2xl font-semibold">Order cancelled</h1>
                    <p>This order was cancelled. If you paid, your refund is on its way.</p>
                </>
            )}

            <p className="text-sm text-slate-600 dark:text-slate-400">
                Order <span className="font-medium">{data.orderNumber}</span> · {formatPrice(data.grandTotal)}
            </p>
            <div className="flex flex-wrap justify-center gap-4 text-sm">
                <Link to={detailLink} className="text-indigo-600 underline dark:text-indigo-400">
                    View order
                </Link>
                <Link to="/shop" className="text-indigo-600 underline dark:text-indigo-400">
                    Continue shopping
                </Link>
            </div>
        </div>
    )
}