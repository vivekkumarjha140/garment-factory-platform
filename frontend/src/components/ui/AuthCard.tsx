import type { ReactNode } from 'react'

interface AuthCardProps {
    title: string
    subtitle?: string
    children: ReactNode
    footer?: ReactNode
}

/** Centered card used by every sign-in related page. */
export function AuthCard({ title, subtitle, children, footer }: AuthCardProps) {
    return (
        <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-6 p-6">
            <div className="text-center">
                <p className="text-sm font-semibold uppercase tracking-wide text-indigo-600 dark:text-indigo-400">
                    Garment Factory Platform
                </p>
                <h1 className="mt-2 text-2xl font-semibold">{title}</h1>
                {subtitle && <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>}
            </div>
            <div className="space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
                {children}
            </div>
            {footer && <div className="text-center text-sm text-slate-600 dark:text-slate-400">{footer}</div>}
        </main>
    )
}