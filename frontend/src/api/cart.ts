import type { AxiosRequestConfig } from 'axios'
import type { AddCartItemRequest, Cart } from '../types/cart'
import { call } from './call'
import { apiClient } from './client'

/** The optional address picks which saved address the shipping estimate is for. Without it the server uses the default shipping address. */
function withAddress(addressId: string | null | undefined): AxiosRequestConfig {
    return { params: { addressId: addressId || undefined } }
}

export const cartApi = {
    get: (addressId?: string | null) => call(apiClient.get<Cart>('/cart', withAddress(addressId))),
    addItem: (body: AddCartItemRequest, addressId?: string | null) =>
        call(apiClient.post<Cart>('/cart/items', body, withAddress(addressId))),
    setQuantity: (itemId: number, quantity: number, addressId?: string | null) =>
        call(apiClient.patch<Cart>(`/cart/items/${itemId}`, { quantity }, withAddress(addressId))),
    removeItem: (itemId: number, addressId?: string | null) =>
        call(apiClient.delete<Cart>(`/cart/items/${itemId}`, withAddress(addressId))),
    clear: (addressId?: string | null) => call(apiClient.delete<Cart>('/cart', withAddress(addressId))),
    applyCoupon: (code: string, addressId?: string | null) =>
        call(apiClient.post<Cart>('/cart/coupon', { code }, withAddress(addressId))),
    removeCoupon: (addressId?: string | null) => call(apiClient.delete<Cart>('/cart/coupon', withAddress(addressId))),
}