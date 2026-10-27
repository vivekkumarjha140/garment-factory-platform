import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError, toApiError, userMessage } from '../../api/errors'
import { ordersApi } from '../../api/orders'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { formatDateTime, formatPrice } from '../../lib/format'
import { usePageTitle } from '../../lib/usePageTitle'
import type { Order, OrderAddress } from '../../types/order'
import { errorText } from '../checkout/errorText'
import { payOnline, type PaymentNotice } from '../checkout/payOnline'
import { OrderStatusBadge } from './OrderStatusBadge'
import { canCancel, canPay, invoiceAvailable, statusText, trackingSteps, visibleHistory } from './orderLogic'
import { orderKeys } from './queryKeys'
import { saveBlob } from './saveBlob'

function AddressBlock({ title, address }: { title: string; address: OrderAddress }) {
    return (
        <div className="space-y-1 text-sm">
            <h3 className="font-medium">{title}</h3>
            <p>{address.recipientName}</p>
            <p>{address.line1}</p>
            {address.line2 && <p>{address.line2}</p>}
            <p>
                {address.city}, {address.state} {address.pincode}
            </p>
            <p>Phone: {address.phone}</p>
        </div>
    )
}

function Row({ label, value, good = false }: { label: string; value: string; good?: boolean }) {
    return (
        <div className="flex justify-between gap-4">
            <dt className="text-slate-600 dark:text-slate-400">{label}</dt>
            <dd className={good ? 'font-medium text-green-700 dark:text-green-400' : ''}>{value}</dd>
        </div>
    )
}

function Tracking({ order }: { order: Order }) {
    if (order.orderStatus === 'CANCELLED') {
        return <p className="text-sm">This order was cancelled.</p>
    }
    return (
        <ol aria-label="Order progress" className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
            {trackingSteps(order).map((step) => (
                <li
                    key={step.key}
                    aria-current={step.state === 'current' ? 'step' : undefined}
                    className={
                        step.state === 'done'
                            ? 'font-medium text-green-700 dark:text-green-400'
                            : step.state === 'current'
                                ? 'font-medium'
                                : 'text-slate-500 dark:text-slate-500'
                    }
                >
                    {step.state === 'done' ? '✓ ' : '○ '}
                    {step.label}
                </li>
            ))}
        </ol>
    )
}

export function OrderDetailPage() {
    const { orderNumber = '' } = useParams()
    usePageTitle(`Order ${orderNumber}`)
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const location = useLocation()
    const [notice, setNotice] = useState<PaymentNotice | null>((location.state as { notice?: PaymentNotice } | null)?.notice ?? null)
    const [paying, setPaying] = useState(false)
    const [payError, setPayError] = useState<string | null>(null)
    const [confirmingCancel, setConfirmingCancel] = useState(false)
    const [reason, setReason] = useState('')
    const [downloading, setDownloading] = useState(false)
    const [downloadError, setDownloadError] = useState<string | null>(null)

    const order = useQuery({ queryKey: orderKeys.detail(orderNumber), queryFn: () => ordersApi.get(orderNumber) })
    const cancel = useMutation({
        mutationFn: () => ordersApi.cancel(orderNumber, reason.trim() || undefined),
        onSuccess: (updated) => {
            queryClient.setQueryData(orderKeys.detail(orderNumber), updated)
            void queryClient.invalidateQueries({ queryKey: orderKeys.all })
            setConfirmingCancel(false)
        },
    })
    const data = order.data

    async function payNow() {
        setPayError(null)
        setNotice(null)
        setPaying(true)
        try {
            const outcome = await payOnline(orderNumber)
            if (outcome.kind === 'submitted') {
                navigate(`/orders/${orderNumber}/success`)
                return
            }
            setNotice(outcome)
        } catch (caught) {
            setPayError(errorText(caught))
        } finally {
            setPaying(false)
            void order.refetch()
        }
    }

    async function downloadInvoice() {
        setDownloadError(null)
        setDownloading(true)
        try {
            const file = await ordersApi.downloadInvoice(orderNumber)
            saveBlob(file.blob, file.fileName)
        } catch (caught) {
            setDownloadError(errorText(caught))
        } finally {
            setDownloading(false)
        }
    }

    if (order.isPending) {
        return <div role="status" aria-label="Loading order" className="h-40 animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
    }
    if (!data) {
        const notFound = order.error instanceof ApiError && order.error.status === 404
        return (
            <div className="space-y-3">
                <Alert variant="error">{notFound ? 'We could not find this order.' : userMessage(toApiError(order.error))}</Alert>
                <Link to="/account/orders" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                    Back to your orders
                </Link>
            </div>
        )
    }

    const paidOnline = data.paymentMethod === 'ONLINE' && data.paymentStatus === 'PAID'

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <Link to="/account/orders" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                        ← Your orders
                    </Link>
                    <h1 className="text-2xl font-semibold">Order {data.orderNumber}</h1>
                    <p className="text-sm text-slate-600 dark:text-slate-400">Placed {formatDateTime(data.placedAt)}</p>
                </div>
                <OrderStatusBadge order={data} />
            </div>

            {notice?.kind === 'dismissed' && (
                <Alert variant="info">Payment was not completed. Your order is saved, so you can pay again any time before it expires.</Alert>
            )}
            {notice?.kind === 'failed' && <Alert variant="error">Payment failed: {notice.message} You can try again.</Alert>}
            {payError && <Alert variant="error">{payError}</Alert>}
            {cancel.isError && <Alert variant="error">{userMessage(toApiError(cancel.error))}</Alert>}
            {downloadError && <Alert variant="error">{downloadError}</Alert>}

            <section aria-label="Progress" className="space-y-3">
                <h2 className="text-lg font-semibold">Tracking</h2>
                <Tracking order={data} />
                <ul aria-label="Status history" className="space-y-1 text-sm text-slate-600 dark:text-slate-400">
                    {visibleHistory(data).map((change, index) => (
                        <li key={`${change.dimension}-${change.toStatus}-${change.at}-${index}`}>
                            {formatDateTime(change.at)} · {statusText(change.toStatus)}
                            {change.reason ? ` (${change.reason})` : ''}
                        </li>
                    ))}
                </ul>
            </section>

            <section aria-label="Items" className="space-y-3">
                <h2 className="text-lg font-semibold">Items</h2>
                <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
                    {data.items.map((item) => (
                        <li key={item.sku} className="flex flex-wrap items-start justify-between gap-3 p-3 text-sm">
                            <div>
                                <p className="font-medium">{item.productName}</p>
                                <p className="text-slate-600 dark:text-slate-400">
                                    {item.color} · Size {item.size} · SKU {item.sku}
                                </p>
                                <p className="text-slate-600 dark:text-slate-400">
                                    {item.quantity} × {formatPrice(item.unitPrice)} · GST {item.taxRate}%
                                </p>
                            </div>
                            <p className="font-medium">{formatPrice(item.lineTotal)}</p>
                        </li>
                    ))}
                </ul>
            </section>

            <div className="grid gap-6 md:grid-cols-2">
                <section aria-label="Addresses" className="space-y-4">
                    <AddressBlock title="Shipping address" address={data.shippingAddress} />
                    <AddressBlock title="Billing address" address={data.billingAddress} />
                </section>
                <section aria-label="Payment summary" className="space-y-3 rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                    <h2 className="text-lg font-semibold">Payment</h2>
                    <dl className="space-y-2 text-sm">
                        <Row label="Subtotal" value={formatPrice(data.subtotal)} />
                        {data.discountTotal > 0 && (
                            <Row label={`Discount${data.couponCode ? ` (${data.couponCode})` : ''}`} value={`−${formatPrice(data.discountTotal)}`} good />
                        )}
                        <Row label="GST" value={formatPrice(data.taxTotal)} />
                        <Row label="Shipping" value={data.shippingTotal === 0 ? 'Free' : formatPrice(data.shippingTotal)} />
                        <div className="flex justify-between gap-4 border-t border-slate-200 pt-2 font-semibold dark:border-slate-800">
                            <dt>Total</dt>
                            <dd data-testid="grand-total">{formatPrice(data.grandTotal)}</dd>
                        </div>
                        {data.amountPaid > 0 && <Row label="Paid" value={formatPrice(data.amountPaid)} />}
                    </dl>
                    <p className="text-sm text-slate-600 dark:text-slate-400">
                        {data.paymentMethod === 'COD'
                            ? `Cash on delivery: pay ${formatPrice(data.grandTotal)} when it arrives.`
                            : paidOnline
                                ? 'Paid online.'
                                : 'Online payment.'}
                    </p>
                    {data.orderStatus === 'CANCELLED' && data.paymentStatus === 'REFUNDED' && (
                        <p className="text-sm">Your refund has been issued. It usually takes a few working days to show in your account.</p>
                    )}
                    {data.orderStatus === 'CANCELLED' && data.paymentStatus === 'PAID' && (
                        <p className="text-sm">Your refund is being processed.</p>
                    )}
                </section>
            </div>

            <div className="flex flex-wrap items-center gap-3">
                {canPay(data) && (
                    <div className="space-y-1">
                        <Button loading={paying} onClick={() => void payNow()}>
                            Pay now · {formatPrice(data.grandTotal)}
                        </Button>
                        {data.paymentDueAt && (
                            <p className="text-xs text-slate-500 dark:text-slate-400">Pay by {formatDateTime(data.paymentDueAt)} or the order is cancelled.</p>
                        )}
                    </div>
                )}
                {invoiceAvailable(data) && (
                    <Button variant="secondary" loading={downloading} onClick={() => void downloadInvoice()}>
                        Download invoice
                    </Button>
                )}
                {canCancel(data) && !confirmingCancel && (
                    <Button variant="secondary" onClick={() => setConfirmingCancel(true)}>
                        Cancel order
                    </Button>
                )}
            </div>

            {canCancel(data) && confirmingCancel && (
                <section aria-label="Cancel order" className="space-y-3 rounded-lg border border-red-300 p-4 dark:border-red-900">
                    <p className="text-sm font-medium">Cancel this order?</p>
                    {paidOnline && <p className="text-sm">You have paid for this order, so it will be refunded in full.</p>}
                    <label className="block space-y-1 text-sm">
                        <span className="font-medium">Reason (optional)</span>
                        <textarea
                            value={reason}
                            maxLength={200}
                            onChange={(event) => setReason(event.target.value)}
                            className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
                        />
                    </label>
                    <div className="flex gap-2">
                        <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>
                            Yes, cancel order
                        </Button>
                        <Button variant="secondary" disabled={cancel.isPending} onClick={() => setConfirmingCancel(false)}>
                            Keep order
                        </Button>
                    </div>
                </section>
            )}
        </div>
    )
}