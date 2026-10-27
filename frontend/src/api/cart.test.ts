import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cartApi } from './cart'
import { apiClient } from './client'

vi.mock('./client', () => ({
    apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))

const reply = { data: { lines: [] } } as never

beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.get).mockResolvedValue(reply)
    vi.mocked(apiClient.post).mockResolvedValue(reply)
    vi.mocked(apiClient.patch).mockResolvedValue(reply)
    vi.mocked(apiClient.delete).mockResolvedValue(reply)
})

describe('cartApi', () => {
    it('loads the cart and leaves addressId out when none is chosen', async () => {
        await cartApi.get()
        expect(apiClient.get).toHaveBeenCalledWith('/cart', { params: { addressId: undefined } })
        await cartApi.get(null)
        expect(apiClient.get).toHaveBeenLastCalledWith('/cart', { params: { addressId: undefined } })
    })

    it('sends the chosen address as a query parameter', async () => {
        await cartApi.get('a1')
        expect(apiClient.get).toHaveBeenCalledWith('/cart', { params: { addressId: 'a1' } })
    })

    it('adds an item', async () => {
        await cartApi.addItem({ variantId: 'v1', quantity: 2 }, null)
        expect(apiClient.post).toHaveBeenCalledWith('/cart/items', { variantId: 'v1', quantity: 2 }, { params: { addressId: undefined } })
    })

    it('changes a quantity', async () => {
        await cartApi.setQuantity(7, 3, 'a1')
        expect(apiClient.patch).toHaveBeenCalledWith('/cart/items/7', { quantity: 3 }, { params: { addressId: 'a1' } })
    })

    it('removes an item and clears the cart', async () => {
        await cartApi.removeItem(7, null)
        expect(apiClient.delete).toHaveBeenCalledWith('/cart/items/7', { params: { addressId: undefined } })
        await cartApi.clear('a1')
        expect(apiClient.delete).toHaveBeenLastCalledWith('/cart', { params: { addressId: 'a1' } })
    })

    it('applies a coupon', async () => {
        await cartApi.applyCoupon('SAVE10', null)
        expect(apiClient.post).toHaveBeenCalledWith('/cart/coupon', { code: 'SAVE10' }, { params: { addressId: undefined } })
    })

    it('removes the coupon', async () => {
        await cartApi.removeCoupon('a1')
        expect(apiClient.delete).toHaveBeenCalledWith('/cart/coupon', { params: { addressId: 'a1' } })
    })

    it('returns the response body', async () => {
        await expect(cartApi.get()).resolves.toEqual({ lines: [] })
    })
})