import { Link } from 'react-router'
import { Button } from '../../components/ui/Button'
import { formatPrice } from '../../lib/format'
import type { CartLine } from '../../types/cart'
import { canDecrease, canIncrease, issueMessage } from './cartLogic'
import { QuantityStepper } from './QuantityStepper'

interface CartLineRowProps {
    line: CartLine
    busy: boolean
    onQuantity: (quantity: number) => void
    onRemove: () => void
}

export function CartLineRow({ line, busy, onQuantity, onRemove }: CartLineRowProps) {
    const message = issueMessage(line)
    const unavailable = line.issue === 'UNAVAILABLE'
    const details = [line.colorName, line.size ? `Size ${line.size}` : null].filter((part): part is string => part !== null)

    return (
        <li className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4 sm:flex-row sm:items-start sm:justify-between dark:border-slate-800">
            <div className="space-y-1">
                {line.productSlug ? (
                    <Link to={`/shop/${line.productSlug}`} className="font-medium hover:underline">
                        {line.productName}
                    </Link>
                ) : (
                    <span className="font-medium">{line.productName}</span>
                )}
                {details.length > 0 && <p className="text-sm text-slate-600 dark:text-slate-400">{details.join(' · ')}</p>}
                {!unavailable && (
                    <p className="text-sm text-slate-600 dark:text-slate-400">
                        {formatPrice(line.unitPrice)} each · GST {line.gstRate}%
                    </p>
                )}
                {message && <p className="text-sm text-red-700 dark:text-red-400">{message}</p>}
            </div>
            <div className="flex flex-wrap items-center gap-4">
                {!unavailable && (
                    <QuantityStepper
                        label={line.productName}
                        value={line.quantity}
                        canDecrease={canDecrease(line)}
                        canIncrease={canIncrease(line)}
                        disabled={busy}
                        onDecrease={() => onQuantity(line.quantity - 1)}
                        onIncrease={() => onQuantity(line.quantity + 1)}
                    />
                )}
                <p className="min-w-20 text-right font-medium">{unavailable ? '—' : formatPrice(line.lineTotal)}</p>
                <Button variant="secondary" disabled={busy} aria-label={`Remove ${line.productName}`} onClick={onRemove}>
                    Remove
                </Button>
            </div>
        </li>
    )
}