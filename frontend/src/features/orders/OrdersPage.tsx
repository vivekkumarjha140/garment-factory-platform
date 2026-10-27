import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { toApiError, userMessage } from '../../api/errors'
import { ordersApi } from '../../api/orders'
import { Alert } from '../../components/ui/Alert'
import { Pagination } from '../../components/ui/Pagination'
import { formatDate, formatPrice } from '../../lib/format'
import { usePageTitle } from '../../lib/usePageTitle'
import { AccountTabs } from '../account/AccountTabs'
import { OrderStatusBadge } from './OrderStatusBadge'
import { orderKeys } from './queryKeys'

const PAGE_SIZE = 10

export function OrdersPage() {
    usePageTitle('Your orders')
    const [page, setPage] = useState(0)
    const orders = useQuery({
        queryKey: orderKeys.list(page),
        queryFn: () => ordersApi.list(page, PAGE_SIZE),
        placeholderData: keepPreviousData,
    })
    const data = orders.data

    return (
        <div className="space-y-6">
            <div className="space-y-4">
                <h1 className="text-2xl font-semibold">Your orders</h1>
                <AccountTabs />
            </div>

            {orders.isPending && <div role="status" aria-label="Loading orders" className="h-24 animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />}
            {orders.isError && !data && <Alert variant="error">{userMessage(toApiError(orders.error))}</Alert>}

            {data && data.items.length === 0 && (
                <div className="space-y-3 rounded-lg border border-dashed border-slate-300 p-8 text-center dark:border-slate-700">
                    <p className="font-medium">You have not placed any orders yet.</p>
                    <Link to="/shop" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                        Browse the shop
                    </Link>
                </div>
            )}

            {data && data.items.length > 0 && (
                <>
                    <ul aria-label="Orders" className="space-y-3">
                        {data.items.map((order) => (
                            <li key={order.orderNumber} className="rounded-lg border border-slate-200 p-4 dark:border-slate-800">
                                <div className="flex flex-wrap items-center justify-between gap-2">
                                    <Link to={`/account/orders/${order.orderNumber}`} className="font-medium text-indigo-600 underline dark:text-indigo-400">
                                        {order.orderNumber}
                                    </Link>
                                    <OrderStatusBadge order={order} />
                                </div>
                                <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
                                    {formatDate(order.placedAt)} · {order.firstItemName ?? 'Order'}
                                    {order.itemCount > 1 ? ` and more (${order.itemCount} items)` : ''}
                                </p>
                                <p className="mt-1 text-sm font-medium">{formatPrice(order.grandTotal)}</p>
                            </li>
                        ))}
                    </ul>
                    <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
                </>
            )}
        </div>
    )
}