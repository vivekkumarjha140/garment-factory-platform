import { Button } from '../../components/ui/Button'
import type { Address, DefaultKind } from '../../types/customer'

interface AddressCardProps {
    address: Address
    /** True while another change is being saved: the buttons wait. */
    busy: boolean
    confirmingDelete: boolean
    onEdit: () => void
    onAskDelete: () => void
    onCancelDelete: () => void
    onConfirmDelete: () => void
    onMakeDefault: (kind: DefaultKind) => void
}

const BADGE =
    'rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-medium text-indigo-800 dark:bg-indigo-950 dark:text-indigo-200'

export function AddressCard({
                                address,
                                busy,
                                confirmingDelete,
                                onEdit,
                                onAskDelete,
                                onCancelDelete,
                                onConfirmDelete,
                                onMakeDefault,
                            }: AddressCardProps) {
    const name = address.label || address.recipientName

    return (
        <li className="space-y-3 rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
            <div className="flex flex-wrap items-center gap-2">
                <h3 className="font-medium">{name}</h3>
                {address.defaultShipping && <span className={BADGE}>Default shipping</span>}
                {address.defaultBilling && <span className={BADGE}>Default billing</span>}
            </div>

            <address className="space-y-0.5 text-sm not-italic text-slate-700 dark:text-slate-300">
                <p>{address.recipientName}</p>
                <p>{address.line1}</p>
                {address.line2 && <p>{address.line2}</p>}
                <p>
                    {address.city}, {address.state} {address.pincode}
                </p>
                <p>Phone: {address.phone}</p>
            </address>

            {confirmingDelete ? (
                <div role="group" aria-label={`Confirm deleting ${name}`} className="flex flex-wrap items-center gap-2 text-sm">
                    <span>Delete this address?</span>
                    <Button variant="danger" disabled={busy} onClick={onConfirmDelete}>
                        Yes, delete
                    </Button>
                    <Button variant="secondary" onClick={onCancelDelete}>
                        Keep it
                    </Button>
                </div>
            ) : (
                <div className="flex flex-wrap gap-2">
                    <Button variant="secondary" disabled={busy} aria-label={`Edit ${name}`} onClick={onEdit}>
                        Edit
                    </Button>
                    {!address.defaultShipping && (
                        <Button
                            variant="secondary"
                            disabled={busy}
                            aria-label={`Make ${name} the default shipping address`}
                            onClick={() => onMakeDefault('shipping')}
                        >
                            Make default shipping
                        </Button>
                    )}
                    {!address.defaultBilling && (
                        <Button
                            variant="secondary"
                            disabled={busy}
                            aria-label={`Make ${name} the default billing address`}
                            onClick={() => onMakeDefault('billing')}
                        >
                            Make default billing
                        </Button>
                    )}
                    <Button variant="secondary" disabled={busy} aria-label={`Delete ${name}`} onClick={onAskDelete}>
                        Delete
                    </Button>
                </div>
            )}
        </li>
    )
}