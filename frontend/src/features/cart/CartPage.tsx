import { useQuery, type UseMutationResult } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { customerApi } from '../../api/customer'
import { toApiError, userMessage, type ApiError } from '../../api/errors'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { usePageTitle } from '../../lib/usePageTitle'
import type { Cart } from '../../types/cart'
import { ADDRESSES_KEY } from '../account/queryKeys'
import { AddressPicker } from './AddressPicker'
import { CartLineRow } from './CartLineRow'
import { CartSummary } from './CartSummary'
import { CouponForm } from './CouponForm'
import { useCart, useCartActions } from './useCart'

export function CartPage() {
    usePageTitle('Your cart')
    const [addressId, setAddressId] = useState<string | null>(null)
    const [confirmClear, setConfirmClear] = useState(false)
    const [actionError, setActionError] = useState<ApiError | null>(null)
    const [couponError, setCouponError] = useState<string | null>(null)

    const cart = useCart(addressId)
    const addresses = useQuery({ queryKey: ADDRESSES_KEY, queryFn: () => customerApi.addresses() })
    const actions = useCartActions(addressId)

    const busy =
        actions.setQuantity.isPending ||
        actions.removeItem.isPending ||
        actions.clear.isPending ||
        actions.applyCoupon.isPending ||
        actions.removeCoupon.isPending

    /** Runs a cart change; on failure shows the reason and reloads the cart so the screen shows the real stock. */
    function act<V>(mutation: UseMutationResult<Cart, Error, V>, variables: V) {
        setActionError(null)
        mutation.mutate(variables, {
            onError: (error) => {
                setActionError(toApiError(error))
                void cart.refetch()
            },
        })
    }

    function applyCoupon(code: string) {
        setCouponError(null)
        actions.applyCoupon.mutate(code, { onError: (error) => setCouponError(userMessage(toApiError(error))) })
    }

    function removeCoupon() {
        setCouponError(null)
        act(actions.removeCoupon, undefined)
    }

    const data = cart.data
    const lines = data?.lines ?? []

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-semibold">Your cart</h1>

            {actionError && <Alert variant="error">{userMessage(actionError)}</Alert>}

            {cart.isPending && (
                <div role="status" aria-label="Loading cart" className="space-y-3">
                    <div className="h-24 animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                    <div className="h-24 animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                </div>
            )}

            {cart.isError && !data && (
                <Alert variant="error">
                    {userMessage(toApiError(cart.error))}{' '}
                    <button type="button" className="underline" onClick={() => void cart.refetch()}>
                        Try again
                    </button>
                </Alert>
            )}

            {data && lines.length === 0 && (
                <div className="space-y-3 rounded-lg border border-dashed border-slate-300 p-8 text-center dark:border-slate-700">
                    <p className="font-medium">Your cart is empty.</p>
                    <Link to="/shop" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                        Browse the shop
                    </Link>
                </div>
            )}

            {data && lines.length > 0 && (
                <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
                    <div className="space-y-4">
                        <AddressPicker
                            addresses={addresses.data ?? []}
                            selectedId={addressId ?? data.shippingAddressId}
                            disabled={busy}
                            onChange={setAddressId}
                        />
                        <ul aria-label="Cart items" className="space-y-3">
                            {lines.map((line) => (
                                <CartLineRow
                                    key={line.itemId}
                                    line={line}
                                    busy={busy}
                                    onQuantity={(quantity) => act(actions.setQuantity, { itemId: line.itemId, quantity })}
                                    onRemove={() => act(actions.removeItem, line.itemId)}
                                />
                            ))}
                        </ul>
                        {confirmClear ? (
                            <div className="flex flex-wrap items-center gap-2 text-sm">
                                <span>Remove all items from your cart?</span>
                                <Button
                                    variant="danger"
                                    disabled={busy}
                                    onClick={() => {
                                        setConfirmClear(false)
                                        act(actions.clear, undefined)
                                    }}
                                >
                                    Yes, clear cart
                                </Button>
                                <Button variant="secondary" onClick={() => setConfirmClear(false)}>
                                    Keep items
                                </Button>
                            </div>
                        ) : (
                            <Button variant="secondary" disabled={busy} onClick={() => setConfirmClear(true)}>
                                Clear cart
                            </Button>
                        )}
                    </div>
                    <div className="space-y-4">
                        <CouponForm
                            coupon={data.coupon}
                            storedCouponError={data.couponError}
                            applyError={couponError}
                            busy={busy}
                            onApply={applyCoupon}
                            onRemove={removeCoupon}
                        />
                        <CartSummary cart={data} />
                    </div>
                </div>
            )}
        </div>
    )
}