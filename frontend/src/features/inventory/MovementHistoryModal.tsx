import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { toApiError, userMessage } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { Alert } from '../../components/ui/Alert'
import { Modal } from '../../components/ui/Modal'
import { Pagination } from '../../components/ui/Pagination'
import { inventoryKeys } from './queryKeys'
import { formatDateTime, formatQuantity, formatSigned } from './quantity'
import { MOVEMENT_LABEL } from './stockStatus'

export type MovementSubject = { kind: 'variant'; id: number } | { kind: 'raw-material'; id: number }

interface Props {
    title: string
    subject: MovementSubject
    onClose: () => void
}

/** The immutable stock ledger for one item, newest first. */
export function MovementHistoryModal({ title, subject, onClose }: Props) {
    const [page, setPage] = useState(0)
    const movements = useQuery({
        queryKey:
            subject.kind === 'variant' ? inventoryKeys.movements(subject.id, page) : inventoryKeys.rawMovements(subject.id, page),
        queryFn: () =>
            subject.kind === 'variant'
                ? inventoryApi.stockMovements(subject.id, page)
                : inventoryApi.rawMaterialMovements(subject.id, page),
        placeholderData: keepPreviousData,
    })
    const data = movements.data

    return (
        <Modal title={title} onClose={onClose} wide>
            {movements.isError && (
                <Alert variant="error">
                    {userMessage(toApiError(movements.error))}{' '}
                    <button type="button" className="underline" onClick={() => void movements.refetch()}>
                        Retry
                    </button>
                </Alert>
            )}
            {movements.isPending && (
                <div role="status" aria-label="Loading history" className="space-y-2">
                    <div className="h-8 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                    <div className="h-8 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                </div>
            )}
            {data && data.items.length === 0 && <p className="text-sm text-slate-500">No stock movements yet.</p>}
            {data && data.items.length > 0 && (
                <div className="space-y-4">
                    <div className="overflow-x-auto rounded-md border border-slate-200 dark:border-slate-800">
                        <table className="w-full text-left text-sm">
                            <caption className="sr-only">Stock movements</caption>
                            <thead className="bg-slate-100 text-xs uppercase text-slate-600 dark:bg-slate-900 dark:text-slate-400">
                            <tr>
                                <th scope="col" className="px-3 py-2">When</th>
                                <th scope="col" className="px-3 py-2">Type</th>
                                <th scope="col" className="px-3 py-2 text-right">On hand</th>
                                <th scope="col" className="px-3 py-2 text-right">Reserved</th>
                                <th scope="col" className="px-3 py-2 text-right">Damaged</th>
                                <th scope="col" className="px-3 py-2 text-right">Balance</th>
                                <th scope="col" className="px-3 py-2">Reason</th>
                            </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                            {data.items.map((movement) => (
                                <tr key={movement.publicId}>
                                    <td className="whitespace-nowrap px-3 py-2">{formatDateTime(movement.createdAt)}</td>
                                    <td className="px-3 py-2">{MOVEMENT_LABEL[movement.movementType]}</td>
                                    <td className="px-3 py-2 text-right">{formatSigned(movement.onHandDelta)}</td>
                                    <td className="px-3 py-2 text-right">{formatSigned(movement.reservedDelta)}</td>
                                    <td className="px-3 py-2 text-right">{formatSigned(movement.damagedDelta)}</td>
                                    <td className="px-3 py-2 text-right">{formatQuantity(movement.balanceOnHandAfter)}</td>
                                    <td className="px-3 py-2">{movement.reason ?? '-'}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                    <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
                </div>
            )}
        </Modal>
    )
}