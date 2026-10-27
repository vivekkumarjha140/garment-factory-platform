import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { toApiError, userMessage } from '../../api/errors'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import type { PublicVariant } from '../../types/catalog'
import { useAuthStatus } from '../auth/useAuthStatus'
import { MAX_LINE_QUANTITY } from './cartLogic'
import { QuantityStepper } from './QuantityStepper'
import { useCartActions } from './useCart'

const LINK_BUTTON =
    'inline-flex items-center justify-center rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400'

interface AddToCartProps {
    /** The chosen colour/size, or undefined while the choice is incomplete. */
    variant: PublicVariant | undefined
    productName: string
}

/** The cart is kept on the server per customer, so signed-out visitors are asked to sign in first. */
export function AddToCart({ variant, productName }: AddToCartProps) {
    const status = useAuthStatus()
    const location = useLocation()
    const { addItem } = useCartActions(null)
    const [quantity, setQuantity] = useState(1)
    const [addedQuantity, setAddedQuantity] = useState<number | null>(null)

    if (status === 'anonymous') {
        return (
            <Link to="/login" state={{ from: location }} className={LINK_BUTTON}>
                Sign in to add to cart
            </Link>
        )
    }

    const error = addItem.isError ? toApiError(addItem.error) : null

    function handleAdd() {
        if (!variant) return
        setAddedQuantity(null)
        addItem.mutate({ variantId: variant.id, quantity }, { onSuccess: () => setAddedQuantity(quantity) })
    }

    return (
        <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-3">
                <QuantityStepper
                    label={productName}
                    value={quantity}
                    canDecrease={quantity > 1}
                    canIncrease={quantity < MAX_LINE_QUANTITY}
                    onDecrease={() => setQuantity((current) => Math.max(1, current - 1))}
                    onIncrease={() => setQuantity((current) => Math.min(MAX_LINE_QUANTITY, current + 1))}
                />
                <Button onClick={handleAdd} disabled={!variant || status !== 'authenticated'} loading={addItem.isPending}>
                    Add to cart
                </Button>
            </div>
            <div aria-live="polite" className="space-y-2">
                {addedQuantity !== null && (
                    <Alert variant="success">
                        Added to your cart.{' '}
                        <Link to="/cart" className="underline">
                            View cart
                        </Link>
                    </Alert>
                )}
                {error && <Alert variant="error">{userMessage(error)}</Alert>}
            </div>
        </div>
    )
}