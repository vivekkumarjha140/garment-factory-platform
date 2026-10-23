import type { ReactNode } from 'react'

type Variant = 'error' | 'success' | 'info'

const STYLES: Record<Variant, string> = {
    error: 'border-red-300 bg-red-50 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200',
    success: 'border-green-300 bg-green-50 text-green-800 dark:border-green-900 dark:bg-green-950 dark:text-green-200',
    info: 'border-slate-300 bg-slate-50 text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200',
}

export function Alert({ variant, children }: { variant: Variant; children: ReactNode }) {
    return (
        <div role={variant === 'error' ? 'alert' : 'status'} className={`rounded-md border px-3 py-2 text-sm ${STYLES[variant]}`}>
            {children}
        </div>
    )
}