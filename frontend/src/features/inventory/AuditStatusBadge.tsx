import type { AuditStatus } from '../../types/inventory'
import { AUDIT_STATUS_LABEL } from './auditLabels'

const STYLES: Record<AuditStatus, string> = {
    IN_PROGRESS: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-950 dark:text-indigo-200',
    COMPLETED: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-200',
    CANCELLED: 'bg-slate-200 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
}

export function AuditStatusBadge({ status }: { status: AuditStatus }) {
    return (
        <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${STYLES[status]}`}>
            {AUDIT_STATUS_LABEL[status]}
        </span>
    )
}