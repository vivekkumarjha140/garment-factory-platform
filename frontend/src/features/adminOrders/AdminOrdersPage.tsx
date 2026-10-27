import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { adminOrdersApi, type AdminOrderFilters } from '../../api/adminOrders'
import { toApiError, userMessage } from '../../api/errors'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Pagination } from '../../components/ui/Pagination'
import { SelectField } from '../../components/ui/SelectField'
import { formatDate, formatPrice } from '../../lib/format'
import { usePageTitle } from '../../lib/usePageTitle'
import type { OrderStatus, PaymentMethod, PaymentStatus, ShipmentStatus } from '../../types/order'
import { OrderStatusBadge } from '../orders/OrderStatusBadge'
import { adminOrderKeys } from './queryKeys'

const PAGE_SIZE = 20

interface Draft {
    q: string
    orderStatus: '' | OrderStatus
    paymentStatus: '' | PaymentStatus
    shipmentStatus: '' | ShipmentStatus
    paymentMethod: '' | PaymentMethod
}

const EMPTY: Draft = { q: '', orderStatus: '', paymentStatus: '', shipmentStatus: '', paymentMethod: '' }

function toFilters(draft: Draft, page: number): AdminOrderFilters {
    return {
        q: draft.q.trim() || undefined,
        orderStatus: draft.orderStatus || undefined,
        paymentStatus: draft.paymentStatus || undefined,
        shipmentStatus: draft.shipmentStatus || undefined,
        paymentMethod: draft.paymentMethod || undefined,
        page,
        size: PAGE_SIZE,
    }
}

export function AdminOrdersPage() {
    usePageTitle('Orders')
    const [draft, setDraft] = useState<Draft>(EMPTY)
    const [applied, setApplied] = useState<Draft>(EMPTY)
    const [page, setPage] = useState(0)
    const filters = toFilters(applied, page)
    const orders = useQuery({
        queryKey: adminOrderKeys.list(filters),
        queryFn: () => adminOrdersApi.list(filters),
        placeholderData: keepPreviousData,
    })
    const data = orders.data

    function search(event: FormEvent) {
        event.preventDefault()
        setApplied(draft)
        setPage(0)
    }

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-semibold">Orders</h1>

            <form onSubmit={search} aria-label="Order filters" className="grid items-end gap-3 sm:grid-cols-2 lg:grid-cols-5">
                <label className="block space-y-1 text-sm">
                    <span className="font-medium">Search</span>
                    <input
                        value={draft.q}
                        onChange={(event) => setDraft({ ...draft, q: event.target.value })}
                        placeholder="Order number, name or email"
                        className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
                    />
                </label>
                <SelectField label="Order status" value={draft.orderStatus} onChange={(event) => setDraft({ ...draft, orderStatus: event.target.value as Draft['orderStatus'] })}>
                    <option value="">All</option>
                    <option value="PENDING_PAYMENT">Awaiting payment</option>
                    <option value="CONFIRMED">Confirmed</option>
                    <option value="PROCESSING">Processing</option>
                    <option value="COMPLETED">Completed</option>
                    <option value="CANCELLED">Cancelled</option>
                </SelectField>
                <SelectField label="Payment status" value={draft.paymentStatus} onChange={(event) => setDraft({ ...draft, paymentStatus: event.target.value as Draft['paymentStatus'] })}>
                    <option value="">All</option>
                    <option value="PENDING">Pending</option>
                    <option value="PAID">Paid</option>
                    <option value="FAILED">Failed</option>
                    <option value="COD_PENDING">Pay on delivery</option>
                    <option value="REFUNDED">Refunded</option>
                </SelectField>
                <SelectField label="Shipment" value={draft.shipmentStatus} onChange={(event) => setDraft({ ...draft, shipmentStatus: event.target.value as Draft['shipmentStatus'] })}>
                    <option value="">All</option>
                    <option value="NOT_SHIPPED">Not shipped</option>
                    <option value="PACKED">Packed</option>
                    <option value="SHIPPED">Shipped</option>
                    <option value="DELIVERED">Delivered</option>
                </SelectField>
                <SelectField label="Payment method" value={draft.paymentMethod} onChange={(event) => setDraft({ ...draft, paymentMethod: event.target.value as Draft['paymentMethod'] })}>
                    <option value="">All</option>
                    <option value="ONLINE">Online</option>
                    <option value="COD">Cash on delivery</option>
                </SelectField>
                <div>
                    <Button type="submit">Search</Button>
                </div>
            </form>

            {orders.isPending && <div role="status" aria-label="Loading orders" className="h-24 animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />}
            {orders.isError && !data && <Alert variant="error">{userMessage(toApiError(orders.error))}</Alert>}
            {data && data.items.length === 0 && <p className="text-sm text-slate-600 dark:text-slate-400">No orders match these filters.</p>}

            {data && data.items.length > 0 && (
                <>
                    <div className="overflow-x-auto">
                        <table aria-label="Orders" className="w-full text-left text-sm">
                            <thead className="border-b border-slate-200 text-slate-600 dark:border-slate-800 dark:text-slate-400">
                            <tr>
                                <th className="py-2 pr-4 font-medium">Order</th>
                                <th className="py-2 pr-4 font-medium">Customer</th>
                                <th className="py-2 pr-4 font-medium">Placed</th>
                                <th className="py-2 pr-4 text-right font-medium">Total</th>
                                <th className="py-2 font-medium">Status</th>
                            </tr>
                            </thead>
                            <tbody>
                            {data.items.map((order) => (
                                <tr key={order.orderNumber} className="border-b border-slate-100 dark:border-slate-900">
                                    <td className="py-2 pr-4">
                                        <Link to={`/admin/orders/${order.orderNumber}`} className="font-medium text-indigo-600 underline dark:text-indigo-400">
                                            {order.orderNumber}
                                        </Link>
                                    </td>
                                    <td className="py-2 pr-4">
                                        <span className="block">{order.customerName}</span>
                                        <span className="block text-xs text-slate-500 dark:text-slate-400">{order.customerEmail}</span>
                                    </td>
                                    <td className="py-2 pr-4">{formatDate(order.placedAt)}</td>
                                    <td className="py-2 pr-4 text-right">{formatPrice(order.grandTotal)}</td>
                                    <td className="py-2">
                                        <OrderStatusBadge order={order} />
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                    <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
                </>
            )}
        </div>
    )
}