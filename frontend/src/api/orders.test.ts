import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './errors'
import { apiClient } from './client'
import { ordersApi } from './orders'

vi.mock('./client', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))

beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.get).mockResolvedValue({ data: {} } as never)
    vi.mocked(apiClient.post).mockResolvedValue({ data: {} } as never)
})

describe('ordersApi', () => {
    it('sends the Idempotency-Key header when placing an order', async () => {
        await ordersApi.place({ shippingAddressId: 'a1', billingAddressId: null, paymentMethod: 'COD' }, 'key-12345678')

        expect(apiClient.post).toHaveBeenCalledWith(
            '/orders',
            { shippingAddressId: 'a1', billingAddressId: null, paymentMethod: 'COD' },
            { headers: { 'Idempotency-Key': 'key-12345678' } },
        )
    })

    it('asks for one page of the history', async () => {
        await ordersApi.list(2, 10)

        expect(apiClient.get).toHaveBeenCalledWith('/orders', { params: { page: 2, size: 10 } })
    })

    it('takes the invoice file name from the response header', async () => {
        const blob = new Blob(['%PDF'])
        vi.mocked(apiClient.get).mockResolvedValue({ data: blob, headers: { 'content-disposition': 'attachment; filename="GF-26-27-000001.pdf"' } } as never)

        const file = await ordersApi.downloadInvoice('GF-2026-000001')

        expect(file.fileName).toBe('GF-26-27-000001.pdf')
        expect(file.blob).toBe(blob)
    })

    it('turns an error that arrives as a blob back into a readable ApiError', async () => {
        const problem = new Blob([JSON.stringify({ code: 'INVOICE_NOT_AVAILABLE', detail: 'An invoice is available once the order is paid.' })])
        vi.mocked(apiClient.get).mockRejectedValue(
            new AxiosError('failed', 'ERR_BAD_REQUEST', undefined, undefined, { status: 409, data: problem, headers: {}, statusText: 'Conflict', config: {} as never }),
        )

        await expect(ordersApi.downloadInvoice('GF-2026-000001')).rejects.toMatchObject({
            code: 'INVOICE_NOT_AVAILABLE',
            status: 409,
        })
        await expect(ordersApi.downloadInvoice('GF-2026-000001')).rejects.toBeInstanceOf(ApiError)
    })
})