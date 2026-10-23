import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { adminApi } from '../../api/admin'
import { toApiError, userMessage } from '../../api/errors'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'

const PAGE_SIZE = 20

function formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleString() : 'Never'
}

/** Read-only user list. The full administration screens arrive in Phase 13. */
export function AdminUsersPage() {
    const [page, setPage] = useState(0)
    const [draft, setDraft] = useState('')
    const [query, setQuery] = useState('')

    const users = useQuery({
        queryKey: ['admin', 'users', query, page],
        queryFn: () => adminApi.listUsers({ q: query, page, size: PAGE_SIZE }),
        placeholderData: keepPreviousData,
    })

    function handleSearch(event: FormEvent) {
        event.preventDefault()
        setPage(0)
        setQuery(draft.trim())
    }

    const data = users.data

    return (
        <div className="space-y-4">
            <h1 className="text-2xl font-semibold">Users</h1>

            <form onSubmit={handleSearch} role="search" className="flex max-w-md gap-2">
                <label htmlFor="user-search" className="sr-only">
                    Search by name or email
                </label>
                <input
                    id="user-search"
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    placeholder="Search by name or email"
                    className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
                />
                <Button type="submit">Search</Button>
            </form>

            {users.isError && (
                <Alert variant="error">
                    {userMessage(toApiError(users.error))}{' '}
                    <button type="button" className="underline" onClick={() => void users.refetch()}>
                        Retry
                    </button>
                </Alert>
            )}

            {users.isPending && (
                <div role="status" aria-label="Loading users" className="space-y-2">
                    <div className="h-10 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                    <div className="h-10 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                    <div className="h-10 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                </div>
            )}

            {data && data.items.length === 0 && <p className="text-sm text-slate-500">No users match this search.</p>}

            {data && data.items.length > 0 && (
                <div className="overflow-x-auto rounded-md border border-slate-200 dark:border-slate-800">
                    <table className="w-full text-left text-sm">
                        <caption className="sr-only">Users</caption>
                        <thead className="bg-slate-100 text-xs uppercase text-slate-600 dark:bg-slate-900 dark:text-slate-400">
                        <tr>
                            <th scope="col" className="px-3 py-2">Name</th>
                            <th scope="col" className="px-3 py-2">Email</th>
                            <th scope="col" className="px-3 py-2">Status</th>
                            <th scope="col" className="px-3 py-2">Roles</th>
                            <th scope="col" className="px-3 py-2">Last sign-in</th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                        {data.items.map((user) => (
                            <tr key={user.publicId}>
                                <td className="px-3 py-2 font-medium">{user.fullName}</td>
                                <td className="px-3 py-2">{user.email}</td>
                                <td className="px-3 py-2">{user.status}</td>
                                <td className="px-3 py-2">{user.roles.join(', ')}</td>
                                <td className="px-3 py-2">{formatDate(user.lastLoginAt)}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            {data && data.totalPages > 1 && (
                <nav aria-label="Pagination" className="flex items-center gap-3 text-sm">
                    <Button variant="secondary" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
                        Previous
                    </Button>
                    <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
                    <Button variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage((current) => current + 1)}>
                        Next
                    </Button>
                </nav>
            )}
        </div>
    )
}