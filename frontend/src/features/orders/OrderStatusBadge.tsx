import { orderBadge, type StatusFields, type Tone } from './orderLogic'

const TONES: Record<Tone, string> = {
    green: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-200',
    amber: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200',
    red: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-200',
    blue: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-200',
    slate: 'bg-slate-200 text-slate-800 dark:bg-slate-800 dark:text-slate-200',
}

export function OrderStatusBadge({ order }: { order: StatusFields }) {
    const badge = orderBadge(order)
    return <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${TONES[badge.tone]}`}>{badge.label}</span>
}