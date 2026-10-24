import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { toApiError, userMessage } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Pagination } from '../../components/ui/Pagination'
import type { StockItem } from '../../types/inventory'
import { Permission } from '../auth/permissions'
import { usePermissions } from '../auth/usePermissions'
import { AddToInventoryModal } from './AddToInventoryModal'
import { InventoryTabs } from './InventoryTabs'
import { MovementHistoryModal } from './MovementHistoryModal'
import { inventoryKeys } from './queryKeys'
import { formatQuantity } from './quantity'
import { StockActionModal, type StockAction } from './StockActionForms'
import { StockBadge } from './StockBadge'
import { summarize, variantLabel } from './stockStatus'

const PAGE_SIZE = 20

type Dialog =
    | { kind: 'action'; item: StockItem; action: StockAction }
    | { kind: 'history'; item: StockItem }
    | { kind: 'add' }
    | null

const SMALL = 'px-2 py-1 text-xs'

export function StockDashboardPage() {
    const { can } = usePermissions()
    const canAdjust = can(Permission.STOCK_ADJUST)
    const [page, setPage] = useState(0)
    const [draft, setDraft] = useState('')
    const [query, setQuery] = useState('')
    const [lowOnly, setLowOnly] = useState(false)
    const [dialog, setDialog] = useState<Dialog>(null)
    const [notice, setNotice] = useState<string | null>(null)

    const params = { q: query, lowStockOnly: lowOnly, page, size: PAGE_SIZE }
    const stock = useQuery({
        queryKey: inventoryKeys.stockList(params),
        queryFn: () => inventoryApi.listStock(params),
        placeholderData: keepPreviousData,
    })
    const data = stock.data
    const summary = data ? summarize(data.items) : null

    function handleSearch(event: FormEvent) {
        event.preventDefault()
        setPage(0)
        setQuery(draft.trim())
    }

    function openAction(item: StockItem, action: StockAction) {
        setNotice(null)
        setDialog({ kind: 'action', item, action })
    }

    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <h1 className="text-2xl font-semibold">Inventory</h1>
                {canAdjust && (
                    <Button
                        onClick={() => {
                            setNotice(null)
                            setDialog({ kind: 'add' })
                        }}
                    >
                        Add variants to inventory
                    </Button>
                )}
            </div>
            <InventoryTabs />

            <div className="flex flex-wrap items-center gap-4">
                <form onSubmit={handleSearch} role="search" className="flex w-full max-w-md gap-2">
                    <label htmlFor="stock-search" className="sr-only">
                        Search by SKU or product name
                    </label>
                    <input
                        id="stock-search"
                        value={draft}
                        onChange={(event) => setDraft(event.target.value)}
                        placeholder="Search by SKU or product name"
                        className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
                    />
                    <Button type="submit">Search</Button>
                </form>
                <Checkbox
                    label="Low stock only"
                    checked={lowOnly}
                    onChange={(event) => {
                        setLowOnly(event.target.checked)
                        setPage(0)
                    }}
                />
            </div>

            {notice && <Alert variant="success">{notice}</Alert>}

            {stock.isError && (
                <Alert variant="error">
                    {userMessage(toApiError(stock.error))}{' '}
                    <button type="button" className="underline" onClick={() => void stock.refetch()}>
                        Retry
                    </button>
                </Alert>
            )}

            {stock.isPending && (
                <div role="status" aria-label="Loading stock" className="space-y-2">
                    <div className="h-10 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                    <div className="h-10 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                    <div className="h-10 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                </div>
            )}

            {data && summary && (
                <p className="text-sm text-slate-600 dark:text-slate-400">
                    {data.totalElements} variants in total. On this page: {summary.out} out of stock, {summary.low} running low.
                </p>
            )}

            {data && data.items.length === 0 && (
                <p className="text-sm text-slate-500">
                    {query || lowOnly
                        ? 'No stock matches these filters.'
                        : 'No stock rows yet. Use "Add variants to inventory" to start.'}
                </p>
            )}

            {data && data.items.length > 0 && (
                <div className="overflow-x-auto rounded-md border border-slate-200 dark:border-slate-800">
                    <table className="w-full text-left text-sm">
                        <caption className="sr-only">Finished goods stock</caption>
                        <thead className="bg-slate-100 text-xs uppercase text-slate-600 dark:bg-slate-900 dark:text-slate-400">
                        <tr>
                            <th scope="col" className="px-3 py-2">Product</th>
                            <th scope="col" className="px-3 py-2">SKU</th>
                            <th scope="col" className="px-3 py-2 text-right">On hand</th>
                            <th scope="col" className="px-3 py-2 text-right">Reserved</th>
                            <th scope="col" className="px-3 py-2 text-right">Available</th>
                            <th scope="col" className="px-3 py-2 text-right">Damaged</th>
                            <th scope="col" className="px-3 py-2 text-right">Alert at</th>
                            <th scope="col" className="px-3 py-2">Status</th>
                            <th scope="col" className="px-3 py-2">Actions</th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {data.items.map((item) => {
                            const label = variantLabel(item)
                            return (
                                <tr key={item.variantId}>
                                    <td className="px-3 py-2 font-medium">{label}</td>
                                    <td className="px-3 py-2">{item.sku ?? '-'}</td>
                                    <td className="px-3 py-2 text-right">{formatQuantity(item.onHand)}</td>
                                    <td className="px-3 py-2 text-right">{formatQuantity(item.reserved)}</td>
                                    <td className="px-3 py-2 text-right font-medium">{formatQuantity(item.available)}</td>
                                    <td className="px-3 py-2 text-right">{formatQuantity(item.damaged)}</td>
                                    <td className="px-3 py-2 text-right">{formatQuantity(item.lowStockThreshold)}</td>
                                    <td className="px-3 py-2">
                                        <StockBadge available={item.available} lowStockThreshold={item.lowStockThreshold} />
                                    </td>
                                    <td className="px-3 py-2">
                                        <div className="flex flex-wrap gap-1">
                                            {canAdjust && (
                                                <>
                                                    <Button variant="secondary" className={SMALL} aria-label={`Adjust stock for ${label}`} onClick={() => openAction(item, 'adjust')}>
                                                        Adjust
                                                    </Button>
                                                    <Button variant="secondary" className={SMALL} aria-label={`Receive stock for ${label}`} onClick={() => openAction(item, 'receive')}>
                                                        Receive
                                                    </Button>
                                                    <Button variant="secondary" className={SMALL} aria-label={`Mark ${label} damaged`} onClick={() => openAction(item, 'damage')}>
                                                        Damaged
                                                    </Button>
                                                    <Button variant="secondary" className={SMALL} aria-label={`Change threshold for ${label}`} onClick={() => openAction(item, 'threshold')}>
                                                        Threshold
                                                    </Button>
                                                </>
                                            )}
                                            <Button variant="secondary" className={SMALL} aria-label={`History for ${label}`} onClick={() => setDialog({ kind: 'history', item })}>
                                                History
                                            </Button>
                                        </div>
                                    </td>
                                </tr>
                            )
                        })}
                        </tbody>
                    </table>
                </div>
            )}

            {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}

            {dialog?.kind === 'action' && (
                <StockActionModal
                    item={dialog.item}
                    action={dialog.action}
                    onClose={() => setDialog(null)}
                    onDone={(message) => {
                        setDialog(null)
                        setNotice(message)
                    }}
                />
            )}
            {dialog?.kind === 'history' && (
                <MovementHistoryModal
                    title={`History: ${variantLabel(dialog.item)}`}
                    subject={{ kind: 'variant', id: dialog.item.variantId }}
                    onClose={() => setDialog(null)}
                />
            )}
            {dialog?.kind === 'add' && <AddToInventoryModal onClose={() => setDialog(null)} />}
        </div>
    )
}