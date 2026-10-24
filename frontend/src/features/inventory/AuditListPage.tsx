import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { toApiError, userMessage, type ApiError } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Pagination } from '../../components/ui/Pagination'
import { SelectField } from '../../components/ui/SelectField'
import { TextField } from '../../components/ui/TextField'
import type { AuditStatus } from '../../types/inventory'
import { AUDIT_STATUS_LABEL } from './auditLabels'
import { AuditStatusBadge } from './AuditStatusBadge'
import { InventoryTabs } from './InventoryTabs'
import { inventoryKeys } from './queryKeys'
import { formatDateTime } from './quantity'
import { auditNotesSchema } from './schemas'

const PAGE_SIZE = 20

export function AuditListPage() {
    const navigate = useNavigate()
    const [status, setStatus] = useState<AuditStatus | ''>('')
    const [page, setPage] = useState(0)
    const [notes, setNotes] = useState('')
    const [notesError, setNotesError] = useState<string | null>(null)
    const [starting, setStarting] = useState(false)
    const [startError, setStartError] = useState<ApiError | null>(null)

    const params = { status: status || undefined, page, size: PAGE_SIZE }
    const audits = useQuery({
        queryKey: inventoryKeys.audits(params),
        queryFn: () => inventoryApi.listAudits(params),
        placeholderData: keepPreviousData,
    })
    const data = audits.data

    async function handleStart(event: FormEvent) {
        event.preventDefault()
        const parsed = auditNotesSchema.safeParse({ notes })
        if (!parsed.success) {
            setNotesError(parsed.error.issues[0]?.message ?? 'Check the notes')
            return
        }
        setNotesError(null)
        setStartError(null)
        setStarting(true)
        try {
            const started = await inventoryApi.startAudit(parsed.data.notes || undefined)
            navigate(`/admin/inventory/audits/${started.publicId}`)
        } catch (caught) {
            setStartError(toApiError(caught))
            setStarting(false)
        }
    }

    return (
        <div className="space-y-4">
            <h1 className="text-2xl font-semibold">Stock audits</h1>
            <InventoryTabs />

            <form onSubmit={(event) => void handleStart(event)} className="max-w-xl space-y-3 rounded-md border border-slate-200 p-4 dark:border-slate-800">
                <h2 className="text-lg font-semibold">Start a count</h2>
                {startError && <Alert variant="error">{userMessage(startError)}</Alert>}
                <TextField
                    label="Notes (optional)"
                    autoComplete="off"
                    hint="For example: Q4 full warehouse count."
                    error={notesError ?? undefined}
                    value={notes}
                    onChange={(event) => setNotes(event.target.value)}
                />
                <Button type="submit" loading={starting}>
                    Start new audit
                </Button>
            </form>

            <div className="w-56">
                <SelectField
                    label="Status"
                    value={status}
                    onChange={(event) => {
                        setStatus(event.target.value as AuditStatus | '')
                        setPage(0)
                    }}
                >
                    <option value="">All audits</option>
                    {(Object.keys(AUDIT_STATUS_LABEL) as AuditStatus[]).map((value) => (
                        <option key={value} value={value}>
                            {AUDIT_STATUS_LABEL[value]}
                        </option>
                    ))}
                </SelectField>
            </div>

            {audits.isError && (
                <Alert variant="error">
                    {userMessage(toApiError(audits.error))}{' '}
                    <button type="button" className="underline" onClick={() => void audits.refetch()}>
                        Retry
                    </button>
                </Alert>
            )}

            {audits.isPending && (
                <div role="status" aria-label="Loading audits" className="space-y-2">
                    <div className="h-10 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                    <div className="h-10 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                </div>
            )}

            {data && data.items.length === 0 && <p className="text-sm text-slate-500">No audits yet.</p>}

            {data && data.items.length > 0 && (
                <div className="overflow-x-auto rounded-md border border-slate-200 dark:border-slate-800">
                    <table className="w-full text-left text-sm">
                        <caption className="sr-only">Stock audits</caption>
                        <thead className="bg-slate-100 text-xs uppercase text-slate-600 dark:bg-slate-900 dark:text-slate-400">
                        <tr>
                            <th scope="col" className="px-3 py-2">Audit</th>
                            <th scope="col" className="px-3 py-2">Status</th>
                            <th scope="col" className="px-3 py-2">Started</th>
                            <th scope="col" className="px-3 py-2">Finished</th>
                            <th scope="col" className="px-3 py-2">Notes</th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {data.items.map((audit) => (
                            <tr key={audit.publicId}>
                                <td className="px-3 py-2 font-medium">
                                    <Link to={`/admin/inventory/audits/${audit.publicId}`} className="text-indigo-600 underline dark:text-indigo-400">
                                        {audit.auditNumber}
                                    </Link>
                                </td>
                                <td className="px-3 py-2">
                                    <AuditStatusBadge status={audit.status} />
                                </td>
                                <td className="px-3 py-2">{formatDateTime(audit.startedAt)}</td>
                                <td className="px-3 py-2">{audit.completedAt ? formatDateTime(audit.completedAt) : '-'}</td>
                                <td className="px-3 py-2">{audit.notes ?? '-'}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
        </div>
    )
}