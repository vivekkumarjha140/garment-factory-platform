import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { toApiError, userMessage } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { Pagination } from '../../components/ui/Pagination'
import { SelectField } from '../../components/ui/SelectField'
import { formatPrice } from '../../lib/format'
import type { RawMaterial, RawMaterialCategory } from '../../types/inventory'
import { Permission } from '../auth/permissions'
import { usePermissions } from '../auth/usePermissions'
import { InventoryTabs } from './InventoryTabs'
import { MovementHistoryModal } from './MovementHistoryModal'
import { inventoryKeys } from './queryKeys'
import { formatQuantity } from './quantity'
import { RawMaterialModal, type RawMaterialDialog } from './RawMaterialForms'
import { RAW_MATERIAL_CATEGORIES, categoryLabel } from './rawMaterialSchemas'
import { StockBadge } from './StockBadge'

const PAGE_SIZE = 20
const SMALL = 'px-2 py-1 text-xs'

export function RawMaterialsPage() {
    const { can } = usePermissions()
    const canAdjust = can(Permission.STOCK_ADJUST)
    const [page, setPage] = useState(0)
    const [category, setCategory] = useState<RawMaterialCategory | ''>('')
    const [lowOnly, setLowOnly] = useState(false)
    const [dialog, setDialog] = useState<RawMaterialDialog | null>(null)
    const [history, setHistory] = useState<RawMaterial | null>(null)
    const [notice, setNotice] = useState<string | null>(null)

    const params = { category: category || undefined, lowStockOnly: lowOnly, page, size: PAGE_SIZE }
    const materials = useQuery({
        queryKey: inventoryKeys.rawMaterials(params),
        queryFn: () => inventoryApi.listRawMaterials(params),
        placeholderData: keepPreviousData,
    })
    const data = materials.data

    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <h1 className="text-2xl font-semibold">Raw materials</h1>
                {canAdjust && (
                    <Button
                        onClick={() => {
                            setNotice(null)
                            setDialog({ kind: 'create' })
                        }}
                    >
                        Add raw material
                    </Button>
                )}
            </div>
            <InventoryTabs />

            <div className="flex flex-wrap items-end gap-4">
                <div className="w-56">
                    <SelectField
                        label="Filter by category"
                        value={category}
                        onChange={(event) => {
                            setCategory(event.target.value as RawMaterialCategory | '')
                            setPage(0)
                        }}
                    >
                        <option value="">All categories</option>
                        {RAW_MATERIAL_CATEGORIES.map((entry) => (
                            <option key={entry.value} value={entry.value}>
                                {entry.label}
                            </option>
                        ))}
                    </SelectField>
                </div>
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

            {materials.isError && (
                <Alert variant="error">
                    {userMessage(toApiError(materials.error))}{' '}
                    <button type="button" className="underline" onClick={() => void materials.refetch()}>
                        Retry
                    </button>
                </Alert>
            )}

            {materials.isPending && (
                <div role="status" aria-label="Loading raw materials" className="space-y-2">
                    <div className="h-10 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                    <div className="h-10 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                </div>
            )}

            {data && data.items.length === 0 && (
                <p className="text-sm text-slate-500">
                    {category || lowOnly ? 'No raw materials match these filters.' : 'No raw materials yet.'}
                </p>
            )}

            {data && data.items.length > 0 && (
                <div className="overflow-x-auto rounded-md border border-slate-200 dark:border-slate-800">
                    <table className="w-full text-left text-sm">
                        <caption className="sr-only">Raw materials</caption>
                        <thead className="bg-slate-100 text-xs uppercase text-slate-600 dark:bg-slate-900 dark:text-slate-400">
                        <tr>
                            <th scope="col" className="px-3 py-2">Code</th>
                            <th scope="col" className="px-3 py-2">Name</th>
                            <th scope="col" className="px-3 py-2">Category</th>
                            <th scope="col" className="px-3 py-2 text-right">On hand</th>
                            <th scope="col" className="px-3 py-2 text-right">Reserved</th>
                            <th scope="col" className="px-3 py-2 text-right">Available</th>
                            <th scope="col" className="px-3 py-2 text-right">Unit cost</th>
                            <th scope="col" className="px-3 py-2">Status</th>
                            <th scope="col" className="px-3 py-2">Actions</th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {data.items.map((item) => (
                            <tr key={item.id}>
                                <td className="px-3 py-2">{item.code}</td>
                                <td className="px-3 py-2 font-medium">{item.name}</td>
                                <td className="px-3 py-2">{categoryLabel(item.category)}</td>
                                <td className="px-3 py-2 text-right">
                                    {formatQuantity(item.onHand)} {item.uom}
                                </td>
                                <td className="px-3 py-2 text-right">{formatQuantity(item.reserved)}</td>
                                <td className="px-3 py-2 text-right font-medium">{formatQuantity(item.available)}</td>
                                <td className="px-3 py-2 text-right">{formatPrice(item.unitCost)}</td>
                                <td className="px-3 py-2">
                                    <StockBadge available={item.available} lowStockThreshold={item.lowStockThreshold} />
                                </td>
                                <td className="px-3 py-2">
                                    <div className="flex flex-wrap gap-1">
                                        {canAdjust && (
                                            <>
                                                <Button
                                                    variant="secondary"
                                                    className={SMALL}
                                                    aria-label={`Receive ${item.name}`}
                                                    onClick={() => {
                                                        setNotice(null)
                                                        setDialog({ kind: 'receive', item })
                                                    }}
                                                >
                                                    Receive
                                                </Button>
                                                <Button
                                                    variant="secondary"
                                                    className={SMALL}
                                                    aria-label={`Adjust ${item.name}`}
                                                    onClick={() => {
                                                        setNotice(null)
                                                        setDialog({ kind: 'adjust', item })
                                                    }}
                                                >
                                                    Adjust
                                                </Button>
                                            </>
                                        )}
                                        <Button variant="secondary" className={SMALL} aria-label={`History for ${item.name}`} onClick={() => setHistory(item)}>
                                            History
                                        </Button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}

            {dialog && (
                <RawMaterialModal
                    dialog={dialog}
                    onClose={() => setDialog(null)}
                    onDone={(message) => {
                        setDialog(null)
                        setNotice(message)
                    }}
                />
            )}
            {history && (
                <MovementHistoryModal
                    title={`History: ${history.name}`}
                    subject={{ kind: 'raw-material', id: history.id }}
                    onClose={() => setHistory(null)}
                />
            )}
        </div>
    )
}