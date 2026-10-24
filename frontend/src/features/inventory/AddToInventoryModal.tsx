import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { toApiError, userMessage, type ApiError } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { TextField } from '../../components/ui/TextField'
import type { UninitializedVariant } from '../../types/inventory'
import { inventoryKeys } from './queryKeys'
import { plural } from './quantity'
import { thresholdSchema } from './schemas'

type Threshold = { ok: true; value: number | undefined } | { ok: false }

/** New variants get a stock row automatically. This covers older ones and lets staff set a custom alert level. */
export function AddToInventoryModal({ onClose }: { onClose: () => void }) {
    const queryClient = useQueryClient()
    const [draft, setDraft] = useState('')
    const [query, setQuery] = useState('')
    const [threshold, setThreshold] = useState('')
    const [thresholdError, setThresholdError] = useState<string | null>(null)
    const [busy, setBusy] = useState<number | 'all' | null>(null)
    const [message, setMessage] = useState<string | null>(null)
    const [error, setError] = useState<ApiError | null>(null)

    const variants = useQuery({
        queryKey: inventoryKeys.uninitialized(query),
        queryFn: () => inventoryApi.uninitializedVariants(query),
    })

    function readThreshold(): Threshold {
        if (threshold.trim() === '') {
            setThresholdError(null)
            return { ok: true, value: undefined }
        }
        const parsed = thresholdSchema.safeParse({ lowStockThreshold: threshold })
        if (!parsed.success) {
            setThresholdError(parsed.error.issues[0]?.message ?? 'Enter a whole number, 0 or more')
            return { ok: false }
        }
        setThresholdError(null)
        return { ok: true, value: Number(parsed.data.lowStockThreshold) }
    }

    async function run(target: number | 'all', work: (value: number | undefined) => Promise<string>) {
        const level = readThreshold()
        if (!level.ok) return
        setBusy(target)
        setMessage(null)
        setError(null)
        try {
            const done = await work(level.value)
            await queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
            setMessage(done)
        } catch (caught) {
            setError(toApiError(caught))
        } finally {
            setBusy(null)
        }
    }

    function add(variant: UninitializedVariant) {
        void run(variant.variantId, async (value) => {
            await inventoryApi.initializeStock(variant.variantId, value)
            return `Added ${variant.sku} to inventory.`
        })
    }

    function addAll() {
        void run('all', async (value) => {
            const result = await inventoryApi.initializeMissing(value)
            return `${plural(result.created, 'stock row')} created.`
        })
    }

    function handleSearch(event: FormEvent) {
        event.preventDefault()
        setQuery(draft.trim())
    }

    return (
        <Modal title="Add variants to inventory" onClose={onClose} wide>
            <div className="space-y-4">
                <TextField
                    label="Alert level for new stock rows (optional)"
                    inputMode="numeric"
                    autoComplete="off"
                    hint="Leave empty to use the default."
                    error={thresholdError ?? undefined}
                    value={threshold}
                    onChange={(event) => setThreshold(event.target.value)}
                />

                <form onSubmit={handleSearch} role="search" className="flex gap-2">
                    <label htmlFor="uninitialized-search" className="sr-only">
                        Find a variant without stock
                    </label>
                    <input
                        id="uninitialized-search"
                        value={draft}
                        onChange={(event) => setDraft(event.target.value)}
                        placeholder="SKU or product name"
                        className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-950"
                    />
                    <Button type="submit" variant="secondary">
                        Find
                    </Button>
                </form>

                {message && <Alert variant="success">{message}</Alert>}
                {error && <Alert variant="error">{userMessage(error)}</Alert>}
                {variants.isError && <Alert variant="error">{userMessage(toApiError(variants.error))}</Alert>}

                {variants.data && variants.data.length === 0 && (
                    <p className="text-sm text-slate-500">Every matching variant already has a stock row.</p>
                )}
                {variants.data && variants.data.length > 0 && (
                    <ul className="divide-y divide-slate-200 rounded-md border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
                        {variants.data.map((variant) => (
                            <li key={variant.variantId} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                                <span>
                                    <span className="font-medium">{variant.productName}</span> ({variant.size} / {variant.colorName}){' '}
                                    <span className="text-slate-500">{variant.sku}</span>
                                </span>
                                <Button
                                    variant="secondary"
                                    className="px-2 py-1 text-xs"
                                    aria-label={`Add ${variant.sku} to inventory`}
                                    loading={busy === variant.variantId}
                                    disabled={busy !== null}
                                    onClick={() => add(variant)}
                                >
                                    Add
                                </Button>
                            </li>
                        ))}
                    </ul>
                )}

                <div className="flex justify-between gap-2 pt-2">
                    <Button variant="secondary" loading={busy === 'all'} disabled={busy !== null} onClick={addAll}>
                        Add all missing variants
                    </Button>
                    <Button variant="secondary" onClick={onClose}>
                        Done
                    </Button>
                </div>
            </div>
        </Modal>
    )
}