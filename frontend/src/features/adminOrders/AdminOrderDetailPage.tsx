import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { adminOrdersApi } from '../../api/adminOrders'
import { ApiError, toApiError, userMessage } from '../../api/errors'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { formatDateTime, formatPrice } from '../../lib/format'
import { usePageTitle } from '../../lib/usePageTitle'
import type { AdminOrder, OrderAddress } from '../../types/order'
import { errorText } from '../checkout/errorText'
import { Permission } from '../auth/permissions'
import { usePermissions } from '../auth/usePermissions'
import { OrderStatusBadge } from '../orders/OrderStatusBadge'
import { invoiceAvailable, needsCashCollection, nextFulfilmentAction, staffCanCancel, statusText, visibleHistory } from '../orders/orderLogic'
import { saveBlob } from '../orders/saveBlob'
import { adminOrderKeys } from './queryKeys'

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

function Row({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex justify-between gap-4">
            <dt className="text-slate-600 dark:text-slate-400">{label}</dt>
            <dd>{value}</dd>
        </div>
    )
}

const ACTION_LABEL = { start: 'Start processing', pack: 'Mark packed', ship: 'Mark shipped', deliver: 'Mark delivered' } as const

export function AdminOrderDetailPage() {
    const { orderNumber = '' } = useParams()
    usePageTitle(`Order ${orderNumber}`)
    const { can } = usePermissions()
    const queryClient = useQueryClient()
    const [cashCollected, setCashCollected] = useState(false)
    const [confirmingCancel, setConfirmingCancel] = useState(false)
    const [reason, setReason] = useState('')
    const [downloadError, setDownloadError] = useState<string | null>(null)

    const detail = useQuery({ queryKey: adminOrderKeys.detail(orderNumber), queryFn: () => adminOrdersApi.get(orderNumber) })
    const act = useMutation({
        mutationFn: (run: () => Promise<AdminOrder>) => run(),
        onSuccess: (updated) => {
            queryClient.setQueryData(adminOrderKeys.detail(orderNumber), updated)
            void queryClient.invalidateQueries({ queryKey: adminOrderKeys.lists })
            setConfirmingCancel(false)
            setCashCollected(false)
        },
    })

    async function downloadInvoice() {
        setDownloadError(null)
        try {
            const file = await adminOrdersApi.downloadInvoice(orderNumber)
            saveBlob(file.blob, file.fileName)
        } catch (caught) {
            setDownloadError(errorText(caught))
        }
    }

    if (detail.isPending) {
        return <div role="status" aria-label="Loading order" className="h-40 animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
    }
    if (!detail.data) {
        const notFound = detail.error instanceof ApiError && detail.error.status === 404
        return (
            <div className="space-y-3">
                <Alert variant="error">{notFound ? 'We could not find this order.' : userMessage(toApiError(detail.error))}</Alert>
                <Link to="/admin/orders" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                    Back to orders
                </Link>
            </div>
        )
    }

    const { order, customer } = detail.data
    const action = nextFulfilmentAction(order)
    const collecting = needsCashCollection(order)
    const paidOnline = order.paymentMethod === 'ONLINE' && order.paymentStatus === 'PAID'

    return (
        <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <Link to="/admin/orders" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                        ← Orders
                    </Link>
                    <h1 className="text-2xl font-semibold">Order {order.orderNumber}</h1>
                    <p className="text-sm text-slate-600 dark:text-slate-400">Placed {formatDateTime(order.placedAt)}</p>
                </div>
                <OrderStatusBadge order={order} />
            </div>

            {act.isError && <Alert variant="error">{userMessage(toApiError(act.error))}</Alert>}
            {downloadError && <Alert variant="error">{downloadError}</Alert>}

            <section aria-label="Customer" className="space-y-1 text-sm">
                <h2 className="text-lg font-semibold">Customer</h2>
                <p>{customer.name}</p>
                <p>{customer.email}</p>
                {customer.phone && <p>{customer.phone}</p>}
            </section>

            <section aria-label="Actions" className="space-y-3">
                <h2 className="text-lg font-semibold">Actions</h2>
                <div className="flex flex-wrap items-start gap-4">
                    {action && can(Permission.ORDER_FULFIL) && (
                        <div className="space-y-2">
                            {action === 'deliver' && collecting && (
                                <label className="flex items-center gap-2 text-sm">
                                    <input type="checkbox" checked={cashCollected} onChange={(event) => setCashCollected(event.target.checked)} />
                                    <span>Cash of {formatPrice(order.grandTotal)} collected from the customer</span>
                                </label>
                            )}
                            <Button
                                loading={act.isPending}
                                disabled={action === 'deliver' && collecting && !cashCollected}
                                onClick={() =>
                                    act.mutate(() =>
                                        action === 'start'
                                            ? adminOrdersApi.startProcessing(orderNumber)
                                            : action === 'pack'
                                                ? adminOrdersApi.pack(orderNumber)
                                                : action === 'ship'
                                                    ? adminOrdersApi.ship(orderNumber)
                                                    : adminOrdersApi.deliver(orderNumber, cashCollected),
                                    )
                                }
                            >
                                {ACTION_LABEL[action]}
                            </Button>
                        </div>
                    )}
                    {invoiceAvailable(order) && (
                        <Button variant="secondary" onClick={() => void downloadInvoice()}>
                            Download invoice
                        </Button>
                    )}
                    {can(Permission.ORDER_CANCEL) && staffCanCancel(order) && !confirmingCancel && (
                        <Button variant="secondary" onClick={() => setConfirmingCancel(true)}>
                            Cancel order
                        </Button>
                    )}
                </div>
                {!action && !staffCanCancel(order) && <p className="text-sm text-slate-600 dark:text-slate-400">No further action is available for this order.</p>}
                {detail.data.invoiceNumber && <p className="text-sm text-slate-600 dark:text-slate-400">Invoice {detail.data.invoiceNumber}</p>}
            </section>

            {can(Permission.ORDER_CANCEL) && staffCanCancel(order) && confirmingCancel && (
                <section aria-label="Cancel order" className="space-y-3 rounded-lg border border-red-300 p-4 dark:border-red-900">
                    <p className="text-sm font-medium">Cancel this order?</p>
                    {paidOnline && <p className="text-sm">The customer has paid, so a full refund will be started automatically.</p>}
                    <label className="block space-y-1 text-sm">
                        <span className="font-medium">Reason (required)</span>
                        <textarea
                            value={reason}
                            maxLength={200}
                            onChange={(event) => setReason(event.target.value)}
                            className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
                        />
                    </label>
                    <div className="flex gap-2">
                        <Button variant="danger" loading={act.isPending} disabled={reason.trim() === ''} onClick={() => act.mutate(() => adminOrdersApi.cancel(orderNumber, reason.trim()))}>
                            Confirm cancellation
                        </Button>
                        <Button variant="secondary" disabled={act.isPending} onClick={() => setConfirmingCancel(false)}>
                            Keep order
                        </Button>
                    </div>
                </section>
            )}

            <section aria-label="Items" className="space-y-3">
                <h2 className="text-lg font-semibold">Items</h2>
                <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
                    {order.items.map((item) => (
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
                    <AddressBlock title="Shipping address" address={order.shippingAddress} />
                    <AddressBlock title="Billing address" address={order.billingAddress} />
                </section>
                <section aria-label="Payment summary" className="space-y-3 rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                    <h2 className="text-lg font-semibold">Payment</h2>
                    <dl className="space-y-2 text-sm">
                        <Row label="Subtotal" value={formatPrice(order.subtotal)} />
                        {order.discountTotal > 0 && <Row label="Discount" value={`−${formatPrice(order.discountTotal)}`} />}
                        <Row label="GST" value={formatPrice(order.taxTotal)} />
                        <Row label="Shipping" value={order.shippingTotal === 0 ? 'Free' : formatPrice(order.shippingTotal)} />
                        <div className="flex justify-between gap-4 border-t border-slate-200 pt-2 font-semibold dark:border-slate-800">
                            <dt>Total</dt>
                            <dd data-testid="grand-total">{formatPrice(order.grandTotal)}</dd>
                        </div>
                        <Row label="Paid" value={formatPrice(order.amountPaid)} />
                    </dl>
                    <p className="text-sm text-slate-600 dark:text-slate-400">{order.paymentMethod === 'COD' ? 'Cash on delivery' : 'Online payment'}</p>
                </section>
            </div>

            <section aria-label="History" className="space-y-2">
                <h2 className="text-lg font-semibold">History</h2>
                <ul aria-label="Status history" className="space-y-1 text-sm text-slate-600 dark:text-slate-400">
                    {visibleHistory(order).map((change, index) => (
                        <li key={`${change.dimension}-${change.toStatus}-${change.at}-${index}`}>
                            {formatDateTime(change.at)} · {statusText(change.toStatus)}
                            {change.reason ? ` (${change.reason})` : ''}
                        </li>
                    ))}
                </ul>
            </section>
        </div>
    )
}