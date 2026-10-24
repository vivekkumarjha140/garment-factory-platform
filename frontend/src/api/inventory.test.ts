import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import { cleanParams, inventoryApi } from './inventory'

vi.mock('./client', () => ({
    apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))

const get = vi.mocked(apiClient.get)
const post = vi.mocked(apiClient.post)
const patch = vi.mocked(apiClient.patch)

beforeEach(() => {
    vi.clearAllMocks()
    get.mockResolvedValue({ data: { content: [] } } as never)
    post.mockResolvedValue({ data: {} } as never)
    patch.mockResolvedValue({ data: {} } as never)
})

describe('cleanParams', () => {
    it('drops empty values but keeps false and zero', () => {
        expect(cleanParams({ q: '  ', page: 0, lowStockOnly: false, size: undefined, category: null })).toEqual({
            page: 0,
            lowStockOnly: false,
        })
    })

    it('trims text', () => {
        expect(cleanParams({ q: '  tee ' })).toEqual({ q: 'tee' })
    })
})

describe('inventoryApi', () => {
    it('lists stock with a clean query', async () => {
        await inventoryApi.listStock({ q: ' tee ', lowStockOnly: true, page: 2, size: 10 })

        expect(get).toHaveBeenCalledWith('/inventory/stock', { params: { q: 'tee', lowStockOnly: true, page: 2, size: 10 } })
    })

    it('omits an empty search from the stock query', async () => {
        await inventoryApi.listStock({ q: '' })

        expect(get).toHaveBeenCalledWith('/inventory/stock', { params: {} })
    })

    it('returns the response body, not the Axios response', async () => {
        get.mockResolvedValue({ data: { items: [{ variantId: 7 }], totalElements: 1 } } as never)

        await expect(inventoryApi.listStock()).resolves.toEqual({ items: [{ variantId: 7 }], totalElements: 1 })
    })

    it('posts an adjustment to the variant', async () => {
        await inventoryApi.adjustStock(7, { delta: -3, reason: 'cycle count' })

        expect(post).toHaveBeenCalledWith('/inventory/stock/7/adjustments', { delta: -3, reason: 'cycle count' })
    })

    it('posts damaged stock and a receipt to the right endpoints', async () => {
        await inventoryApi.damageStock(7, { quantity: 2, reason: 'water damage' })
        await inventoryApi.receiveStock(7, { quantity: 10, unitCost: null, supplierName: null, supplierReference: null, notes: null })

        expect(post).toHaveBeenNthCalledWith(1, '/inventory/stock/7/damage', { quantity: 2, reason: 'water damage' })
        expect(post).toHaveBeenNthCalledWith(2, '/inventory/stock/7/receipts', expect.objectContaining({ quantity: 10 }))
    })

    it('updates a threshold with PATCH', async () => {
        await inventoryApi.updateThreshold(7, 5)

        expect(patch).toHaveBeenCalledWith('/inventory/stock/7/threshold', { lowStockThreshold: 5 })
    })

    it('searches variants that still need a stock row', async () => {
        await inventoryApi.uninitializedVariants('tee', 10)

        expect(get).toHaveBeenCalledWith('/inventory/stock/uninitialized', { params: { q: 'tee', limit: 10 } })
    })

    it('completes an audit only with an explicit apply flag', async () => {
        await inventoryApi.completeAudit('abc', false)

        expect(post).toHaveBeenCalledWith('/inventory/audits/abc/complete', { applyAdjustments: false })
    })

    it('records counts against the audit', async () => {
        await inventoryApi.countFinishedGood('abc', 7, 8)
        await inventoryApi.countRawMaterial('abc', 3, 14.5)

        expect(post).toHaveBeenNthCalledWith(1, '/inventory/audits/abc/counts/finished-goods', { variantId: 7, countedQuantity: 8 })
        expect(post).toHaveBeenNthCalledWith(2, '/inventory/audits/abc/counts/raw-materials', { rawMaterialId: 3, countedQuantity: 14.5 })
    })
})