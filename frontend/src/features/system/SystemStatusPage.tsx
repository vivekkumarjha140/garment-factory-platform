import { useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { motion } from 'framer-motion'
import { fetchPing } from '../../api/system'
import { ThemeToggle } from '../ui/ThemeToggle'

function Row({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex justify-between gap-4 py-2 text-sm">
            <dt className="text-slate-500 dark:text-slate-400">{label}</dt>
            <dd className="font-medium">{value}</dd>
        </div>
    )
}

export function SystemStatusPage() {
    const { data, isPending, isError, error, refetch, isFetching } = useQuery({
        queryKey: ['system', 'ping'],
        queryFn: fetchPing,
    })

    return (
        <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-6 p-6">
            <header className="flex items-center justify-between">
                <h1 className="text-xl font-semibold">Garment Factory Platform</h1>
                <ThemeToggle />
            </header>

            <motion.section
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2 }}
                className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900"
                aria-live="polite"
            >
                <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    Backend status
                </h2>

                {isPending && (
                    <div role="status" className="space-y-2" aria-label="Loading backend status">
                        <div className="h-4 w-full animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
                        <div className="h-4 w-3/4 animate-pulse rounded bg-slate-200 dark:bg-slate-700" />
                    </div>
                )}

                {isError && (
                    <div role="alert" className="space-y-3">
                        <p className="text-sm text-red-600 dark:text-red-400">
                            Cannot reach the backend: {isAxiosError(error) ? error.message : 'unexpected error'}
                        </p>
                        <button
                            type="button"
                            onClick={() => void refetch()}
                            className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
                        >
                            {isFetching ? 'Retrying…' : 'Retry'}
                        </button>
                    </div>
                )}

                {data && (
                    <dl className="divide-y divide-slate-100 dark:divide-slate-800">
                        <Row label="Application" value={data.application} />
                        <Row label="Version" value={data.version} />
                        <Row label="Profile" value={data.profile} />
                        <Row label="Server time" value={new Date(data.serverTime).toLocaleString()} />
                    </dl>
                )}
            </motion.section>
        </main>
    )
}