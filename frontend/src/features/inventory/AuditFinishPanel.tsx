import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toApiError, userMessage, type ApiError } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { inventoryKeys } from './queryKeys'
import { plural } from './quantity'

interface Props {
    publicId: string
    lineCount: number
    varianceCount: number
    onFinished: (message: string) => void
}

type Confirming = 'apply' | 'cancel' | null

/** Completing with "apply" changes stock, so it asks first. The choice is always explicit. */
export function AuditFinishPanel({ publicId, lineCount, varianceCount, onFinished }: Props) {
    const queryClient = useQueryClient()
    const [confirming, setConfirming] = useState<Confirming>(null)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<ApiError | null>(null)

    async function finish(work: () => Promise<string>) {
        setBusy(true)
        setError(null)
        try {
            const message = await work()
            await queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
            setConfirming(null)
            onFinished(message)
        } catch (caught) {
            setError(toApiError(caught))
        } finally {
            setBusy(false)
        }
    }

    const complete = (apply: boolean) =>
        finish(async () => {
            const result = await inventoryApi.completeAudit(publicId, apply)
            return `Audit completed. ${plural(result.adjustmentsApplied, 'adjustment')} applied.`
        })

    const cancel = () =>
        finish(async () => {
            await inventoryApi.cancelAudit(publicId)
            return 'Audit cancelled. No stock was changed.'
        })

    const noLines = lineCount === 0

    return (
        <section aria-labelledby="finish-title" className="space-y-3 rounded-md border border-slate-200 p-4 dark:border-slate-800">
            <h2 id="finish-title" className="text-lg font-semibold">
                Finish this audit
            </h2>
            {error && <Alert variant="error">{userMessage(error)}</Alert>}
            {noLines && <p className="text-sm text-slate-500">Count at least one item before completing the audit.</p>}

            {confirming === null && (
                <div className="flex flex-wrap gap-2">
                    <Button disabled={noLines} loading={busy} onClick={() => setConfirming('apply')}>
                        Complete and apply variances
                    </Button>
                    <Button variant="secondary" disabled={noLines} loading={busy} onClick={() => void complete(false)}>
                        Complete without changing stock
                    </Button>
                    <Button variant="secondary" onClick={() => setConfirming('cancel')}>
                        Cancel audit
                    </Button>
                </div>
            )}

            {confirming === 'apply' && (
                <div className="space-y-3">
                    <p className="text-sm">
                        This changes stock for {plural(varianceCount, 'item')} with a variance, and each change is written to the stock
                        ledger. It cannot be undone.
                    </p>
                    <div className="flex gap-2">
                        <Button loading={busy} onClick={() => void complete(true)}>
                            Yes, apply variances
                        </Button>
                        <Button variant="secondary" disabled={busy} onClick={() => setConfirming(null)}>
                            Keep working
                        </Button>
                    </div>
                </div>
            )}

            {confirming === 'cancel' && (
                <div className="space-y-3">
                    <p className="text-sm">Cancelling closes this audit without changing any stock.</p>
                    <div className="flex gap-2">
                        <Button variant="danger" loading={busy} onClick={() => void cancel()}>
                            Yes, cancel audit
                        </Button>
                        <Button variant="secondary" disabled={busy} onClick={() => setConfirming(null)}>
                            Keep working
                        </Button>
                    </div>
                </div>
            )}
        </section>
    )
}