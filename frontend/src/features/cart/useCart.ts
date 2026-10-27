import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cartApi } from '../../api/cart'
import type { AddCartItemRequest, Cart } from '../../types/cart'

export const CART_KEY = ['cart'] as const
export const cartKey = (addressId: string | null) => ['cart', addressId ?? 'default'] as const

/** addressId null = the customer's default shipping address (the server picks it). */
export function useCart(addressId: string | null, enabled = true) {
    return useQuery({
        queryKey: cartKey(addressId),
        queryFn: () => cartApi.get(addressId),
        enabled,
        staleTime: 30_000,
    })
}

/** Every cart call answers with the whole fresh cart, which is stored as the query's data. */
export function useCartActions(addressId: string | null) {
    const queryClient = useQueryClient()

    function store(cart: Cart) {
        const key = cartKey(addressId)
        queryClient.setQueryData(key, cart)
        // Other views of the cart (the header badge, another address) must not keep the old lines.
        void queryClient.invalidateQueries({ queryKey: CART_KEY, predicate: (query) => query.queryKey[1] !== key[1] })
    }

    return {
        addItem: useMutation({
            mutationFn: (input: AddCartItemRequest) => cartApi.addItem(input, addressId),
            onSuccess: store,
        }),
        setQuantity: useMutation({
            mutationFn: (input: { itemId: number; quantity: number }) =>
                cartApi.setQuantity(input.itemId, input.quantity, addressId),
            onSuccess: store,
        }),
        removeItem: useMutation({
            mutationFn: (itemId: number) => cartApi.removeItem(itemId, addressId),
            onSuccess: store,
        }),
        clear: useMutation({ mutationFn: () => cartApi.clear(addressId), onSuccess: store }),
        applyCoupon: useMutation({
            mutationFn: (code: string) => cartApi.applyCoupon(code, addressId),
            onSuccess: store,
        }),
        removeCoupon: useMutation({ mutationFn: () => cartApi.removeCoupon(addressId), onSuccess: store }),
    }
}