import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { toApiError, userMessage } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { Alert } from '../../components/ui/Alert'
import { ITEM_TYPE_LABEL } from './auditLabels'
import { FinishedGoodCountForm, RawMaterialCountForm } from './AuditCountForms'
import { AuditFinishPanel } from './AuditFinishPanel'
import { AuditStatusBadge } from './AuditStatusBadge'
import { InventoryTabs } from './InventoryTabs'
import { inventoryKeys } from './queryKeys'
import { formatDateTime, formatQuantity, formatSigned, plural } from './quantity'

function varianceClass(variance: number): string {
    if (variance === 0) return ''
    return variance < 0 ? 'text-red-700 dark:text-red-300' : 'text-green-700 dark:text-green-300'
}

export function AuditDetailPage() {
    const { publicId = '' } = useParams<{ publicId: string }>()
    const [notice, setNotice] = useState<string | null>(null)
    const audit = useQuery({
        queryKey: inventoryKeys.audit(publicId),
        queryFn: () => inventoryApi.getAudit(publicId),
        enabled: publicId !== '',
    })

    if (audit.isPending) {
        return <div role="status" aria-label="Loading audit" className="h-24 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
    }
    if (audit.isError) {
        return (
            <div className="space-y-3">
                <Alert variant="error">
                    {userMessage(toApiError(audit.error))}{' '}
                    <button type="button" className="underline" onClick={() => void audit.refetch()}>
                        Retry
                    </button>
                </Alert>
                <Link to="/admin/inventory/audits" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                    Back to audits
                </Link>
            </div>
        )
    }

    const { audit: summary, lines, linesWithVariance } = audit.data
    const open = summary.status === 'IN_PROGRESS'

    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-3">
                <h1 className="text-2xl font-semibold">{summary.auditNumber}</h1>
                <AuditStatusBadge status={summary.status} />
            </div>
            <InventoryTabs />

            <p className="text-sm text-slate-600 dark:text-slate-400">
                Started {formatDateTime(summary.startedAt)}
                {summary.completedAt ? `, finished ${formatDateTime(summary.completedAt)}` : ''}.
                {summary.notes ? ` Notes: ${summary.notes}` : ''}
            </p>

            {notice && <Alert variant="success">{notice}</Alert>}

            <p className="text-sm">
                {plural(lines.length, 'item')} counted, {plural(linesWithVariance, 'variance')}.
            </p>

            {lines.length === 0 ? (
                <p className="text-sm text-slate-500">Nothing has been counted yet.</p>
            ) : (
                <div className="overflow-x-auto rounded-md border border-slate-200 dark:border-slate-800">
                    <table className="w-full text-left text-sm">
                        <caption className="sr-only">Counted items</caption>
                        <thead className="bg-slate-100 text-xs uppercase text-slate-600 dark:bg-slate-900 dark:text-slate-400">
                        <tr>
                            <th scope="col" className="px-3 py-2">Item</th>
                            <th scope="col" className="px-3 py-2">Type</th>
                            <th scope="col" className="px-3 py-2 text-right">System</th>
                            <th scope="col" className="px-3 py-2 text-right">Counted</th>
                            <th scope="col" className="px-3 py-2 text-right">Variance</th>
                            <th scope="col" className="px-3 py-2">Stock adjusted</th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {lines.map((line) => (
                            <tr key={line.id}>
                                <td className="px-3 py-2">
                                    <span className="font-medium">{line.itemName}</span>
                                    {line.sku && <span className="ml-2 text-slate-500">{line.sku}</span>}
                                </td>
                                <td className="px-3 py-2">{ITEM_TYPE_LABEL[line.itemType]}</td>
                                <td className="px-3 py-2 text-right">{formatQuantity(line.systemQuantity)}</td>
                                <td className="px-3 py-2 text-right">{formatQuantity(line.countedQuantity)}</td>
                                <td className={`px-3 py-2 text-right font-medium ${varianceClass(line.variance)}`}>{formatSigned(line.variance)}</td>
                                <td className="px-3 py-2">{line.adjustmentMovementId === null ? 'No' : 'Yes'}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            {open && (
                <>
                    <div className="grid gap-4 md:grid-cols-2">
                        <FinishedGoodCountForm publicId={publicId} onSaved={setNotice} />
                        <RawMaterialCountForm publicId={publicId} onSaved={setNotice} />
                    </div>
                    <AuditFinishPanel publicId={publicId} lineCount={lines.length} varianceCount={linesWithVariance} onFinished={setNotice} />
                </>
            )}
        </div>
    )
}