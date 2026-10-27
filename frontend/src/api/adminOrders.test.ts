import { beforeEach, describe, expect, it, vi } from 'vitest'
import { adminOrdersApi } from './adminOrders'
import { apiClient } from './client'

vi.mock('./client', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))

beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.get).mockResolvedValue({ data: {} } as never)
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} } as never)
})

describe('adminOrdersApi', () => {
    it('sends the filters as query parameters', async () => {
        await adminOrdersApi.list({ q: 'asha', orderStatus: 'CONFIRMED', page: 1, size: 20 })

        expect(apiClient.get).toHaveBeenCalledWith('/admin/orders', { params: { q: 'asha', orderStatus: 'CONFIRMED', page: 1, size: 20 } })
    })

    it('calls one endpoint per fulfilment step', async () => {
        await adminOrdersApi.startProcessing('GF-1')
        await adminOrdersApi.pack('GF-1')
        await adminOrdersApi.ship('GF-1', 'Left with the courier')
        await adminOrdersApi.deliver('GF-1', true)

        const urls = vi.mocked(apiClient.post).mock.calls.map((call) => call[0])
        expect(urls).toEqual(['/admin/orders/GF-1/start-processing', '/admin/orders/GF-1/pack', '/admin/orders/GF-1/ship', '/admin/orders/GF-1/deliver'])
        expect(vi.mocked(apiClient.post).mock.calls[2][1]).toEqual({ note: 'Left with the courier' })
        expect(vi.mocked(apiClient.post).mock.calls[3][1]).toEqual({ cashCollected: true, note: null })
    })

    it('sends the cancellation reason', async () => {
        await adminOrdersApi.cancel('GF-1', 'customer called')

        expect(apiClient.post).toHaveBeenCalledWith('/admin/orders/GF-1/cancel', { reason: 'customer called' })
    })
})