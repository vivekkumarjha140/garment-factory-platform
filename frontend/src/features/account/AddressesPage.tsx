import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { customerApi } from '../../api/customer'
import { toApiError, userMessage, type ApiError } from '../../api/errors'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { usePageTitle } from '../../lib/usePageTitle'
import type { Address, DefaultKind } from '../../types/customer'
import { AccountTabs } from './AccountTabs'
import { AddressCard } from './AddressCard'
import { AddressForm } from './AddressForm'
import { ADDRESSES_KEY, STATES_KEY } from './queryKeys'

/** The same limit the server enforces; the server's answer is the one that counts. */
const MAX_ADDRESSES = 10

type Editor = { kind: 'create' } | { kind: 'edit'; address: Address } | null

export function AddressesPage() {
    usePageTitle('Saved addresses')
    const queryClient = useQueryClient()
    const addresses = useQuery({ queryKey: ADDRESSES_KEY, queryFn: () => customerApi.addresses() })
    const states = useQuery({ queryKey: STATES_KEY, queryFn: () => customerApi.states(), staleTime: Infinity })

    const [editor, setEditor] = useState<Editor>(null)
    const [deletingId, setDeletingId] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)
    const [actionError, setActionError] = useState<ApiError | null>(null)

    const refresh = () => queryClient.invalidateQueries({ queryKey: ADDRESSES_KEY })

    const remove = useMutation({
        mutationFn: (id: string) => customerApi.deleteAddress(id),
        onMutate: () => {
            setActionError(null)
            setNotice(null)
        },
        onSuccess: () => setNotice('Address deleted.'),
        onError: (error) => setActionError(toApiError(error)),
        onSettled: () => {
            setDeletingId(null)
            void refresh()
        },
    })

    const makeDefault = useMutation({
        mutationFn: (input: { id: string; kind: DefaultKind }) => customerApi.makeDefault(input.id, input.kind),
        onMutate: () => {
            setActionError(null)
            setNotice(null)
        },
        onSuccess: () => setNotice('Default address updated.'),
        onError: (error) => setActionError(toApiError(error)),
        onSettled: () => void refresh(),
    })

    const busy = remove.isPending || makeDefault.isPending
    const list = addresses.data ?? []
    const atLimit = list.length >= MAX_ADDRESSES

    function handleSaved() {
        setEditor(null)
        setActionError(null)
        setNotice('Address saved.')
        void refresh()
    }

    function handleConfirmDelete(id: string) {
        if (editor?.kind === 'edit' && editor.address.id === id) setEditor(null)
        remove.mutate(id)
    }

    return (
        <div className="space-y-6">
            <div className="space-y-4">
                <h1 className="text-2xl font-semibold">Saved addresses</h1>
                <AccountTabs />
            </div>

            {actionError && <Alert variant="error">{userMessage(actionError)}</Alert>}
            {notice && <Alert variant="success">{notice}</Alert>}

            {addresses.isPending && (
                <div role="status" aria-label="Loading addresses" className="space-y-3">
                    <div className="h-32 animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                    <div className="h-32 animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                </div>
            )}

            {addresses.isError && (
                <Alert variant="error">
                    {userMessage(toApiError(addresses.error))}{' '}
                    <button type="button" className="underline" onClick={() => void addresses.refetch()}>
                        Try again
                    </button>
                </Alert>
            )}

            {addresses.data && list.length === 0 && !editor && (
                <div className="space-y-3 rounded-lg border border-dashed border-slate-300 p-8 text-center dark:border-slate-700">
                    <p className="font-medium">You have no saved addresses yet.</p>
                    <p className="text-sm text-slate-600 dark:text-slate-400">Save one now to check out faster later.</p>
                </div>
            )}

            {list.length > 0 && (
                <ul className="grid gap-4 md:grid-cols-2">
                    {list.map((address) => (
                        <AddressCard
                            key={address.id}
                            address={address}
                            busy={busy}
                            confirmingDelete={deletingId === address.id}
                            onEdit={() => setEditor({ kind: 'edit', address })}
                            onAskDelete={() => setDeletingId(address.id)}
                            onCancelDelete={() => setDeletingId(null)}
                            onConfirmDelete={() => handleConfirmDelete(address.id)}
                            onMakeDefault={(kind) => makeDefault.mutate({ id: address.id, kind })}
                        />
                    ))}
                </ul>
            )}

            {states.isError && (
                <Alert variant="error">
                    Could not load the list of states.{' '}
                    <button type="button" className="underline" onClick={() => void states.refetch()}>
                        Try again
                    </button>
                </Alert>
            )}

            {editor ? (
                <AddressForm
                    key={editor.kind === 'edit' ? editor.address.id : 'new'}
                    address={editor.kind === 'edit' ? editor.address : undefined}
                    isFirst={editor.kind === 'create' && list.length === 0}
                    states={states.data}
                    onSaved={handleSaved}
                    onCancel={() => setEditor(null)}
                />
            ) : (
                addresses.data && (
                    <div className="space-y-2">
                        <Button disabled={atLimit} onClick={() => setEditor({ kind: 'create' })}>
                            Add address
                        </Button>
                        {atLimit && (
                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                You can save up to {MAX_ADDRESSES} addresses. Delete one to add another.
                            </p>
                        )}
                    </div>
                )
            )}
        </div>
    )
}