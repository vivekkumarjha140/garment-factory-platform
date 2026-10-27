import { useId } from 'react'
import { Link } from 'react-router'
import type { Address } from '../../types/customer'

function addressText(address: Address): string {
    return `${address.label ?? address.recipientName} · ${address.city}, ${address.state} ${address.pincode}`
}

interface AddressPickerProps {
    addresses: Address[]
    /** The address the shipping estimate is for. */
    selectedId: string | null
    disabled?: boolean
    onChange: (id: string) => void
}

export function AddressPicker({ addresses, selectedId, disabled = false, onChange }: AddressPickerProps) {
    const id = useId()

    if (addresses.length === 0) {
        return (
            <p className="text-sm text-slate-600 dark:text-slate-400">
                Add a delivery address to see shipping charges.{' '}
                <Link to="/account/addresses" className="underline">
                    Manage addresses
                </Link>
            </p>
        )
    }

    return (
        <div className="space-y-1">
            <label htmlFor={id} className="block text-sm font-medium">
                Deliver to
            </label>
            <select
                id={id}
                value={selectedId ?? ''}
                disabled={disabled}
                onChange={(event) => onChange(event.target.value)}
                className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 dark:border-slate-700 dark:bg-slate-950"
            >
                {selectedId === null && (
                    <option value="" disabled>
                        Choose an address
                    </option>
                )}
                {addresses.map((address) => (
                    <option key={address.id} value={address.id}>
                        {addressText(address)}
                    </option>
                ))}
            </select>
        </div>
    )
}