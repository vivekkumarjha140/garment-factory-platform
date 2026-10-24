import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { renderWithProviders } from '../../test/utils'
import type { PageResponse, StockItem, StockMovement, StockSnapshot } from '../../types/inventory'
import { Permission } from '../auth/permissions'
import { StockDashboardPage } from './StockDashboardPage'

vi.mock('../../api/inventory', () => ({
    inventoryApi: {
        listStock: vi.fn(),
        stockMovements: vi.fn(),
        uninitializedVariants: vi.fn(),
        initializeStock: vi.fn(),
        initializeMissing: vi.fn(),
        updateThreshold: vi.fn(),
        receiveStock: vi.fn(),
        adjustStock: vi.fn(),
        damageStock: vi.fn(),
    },
}))

const listStock = vi.mocked(inventoryApi.listStock)
const stockMovements = vi.mocked(inventoryApi.stockMovements)
const adjustStock = vi.mocked(inventoryApi.adjustStock)
const receiveStock = vi.mocked(inventoryApi.receiveStock)
const damageStock = vi.mocked(inventoryApi.damageStock)
const updateThreshold = vi.mocked(inventoryApi.updateThreshold)

const TEE: StockItem = {
    variantId: 7,
    sku: 'TEE-M-RED',
    productName: 'Round Neck Tee',
    size: 'M',
    colorName: 'Red',
    variantStatus: 'ACTIVE',
    publicId: 's7',
    onHand: 10,
    reserved: 2,
    damaged: 1,
    available: 8,
    lowStockThreshold: 5,
    lowStock: false,
    version: 3,
    updatedAt: '2026-10-20T10:00:00Z',
}
const HOODIE: StockItem = {
    ...TEE,
    variantId: 8,
    sku: 'HOOD-L-BLK',
    productName: 'Hoodie',
    size: 'L',
    colorName: 'Black',
    publicId: 's8',
    onHand: 3,
    reserved: 0,
    available: 3,
    lowStock: true,
}
const SOLD_OUT: StockItem = {
    ...TEE,
    variantId: 9,
    sku: 'CAP-OS-BLU',
    productName: 'Cap',
    size: 'OS',
    colorName: 'Blue',
    publicId: 's9',
    onHand: 2,
    reserved: 2,
    available: 0,
    lowStock: true,
}
const SNAPSHOT: StockSnapshot = {
    publicId: 's7',
    variantId: 7,
    onHand: 7,
    reserved: 2,
    damaged: 1,
    available: 5,
    lowStockThreshold: 5,
    lowStock: false,
    version: 4,
    updatedAt: '2026-10-20T10:05:00Z',
}
const MOVEMENT: StockMovement = {
    publicId: 'm1',
    itemType: 'FINISHED_GOOD',
    variantId: 7,
    rawMaterialId: null,
    movementType: 'RECEIPT',
    onHandDelta: 10,
    reservedDelta: 0,
    damagedDelta: 0,
    balanceOnHandAfter: 10,
    referenceType: 'STOCK_RECEIPT',
    referenceId: 'r1',
    reason: 'Goods receipt RCV-1',
    actorUserId: 1,
    createdAt: '2026-10-19T08:00:00Z',
}

const page = <T,>(items: T[]): PageResponse<T> => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 })

const staff = (...permissions: string[]) => ({
    auth: {
        status: 'authenticated' as const,
        user: { publicId: 'u1', email: 'wanda@example.com', fullName: 'Wanda', roles: ['WAREHOUSE_MANAGER'], permissions },
    },
})

const TEE_LABEL = 'Round Neck Tee (M / Red)'

beforeEach(() => {
    vi.clearAllMocks()
    listStock.mockResolvedValue(page([TEE, HOODIE, SOLD_OUT]))
})

describe('StockDashboardPage', () => {
    it('shows each variant with its figures and a status badge', async () => {
        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW) })

        expect(await screen.findByText(TEE_LABEL)).toBeInTheDocument()
        expect(screen.getByText('TEE-M-RED')).toBeInTheDocument()
        expect(screen.getByText('In stock')).toBeInTheDocument()
        expect(screen.getByText('Low stock')).toBeInTheDocument()
        expect(screen.getByText('Out of stock')).toBeInTheDocument()
        expect(screen.getByText('3 variants in total. On this page: 1 out of stock, 1 running low.')).toBeInTheDocument()
    })

    it('hides the stock-changing buttons from someone who can only view', async () => {
        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW) })

        await screen.findByText(TEE_LABEL)
        expect(screen.queryByRole('button', { name: `Adjust stock for ${TEE_LABEL}` })).not.toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Add variants to inventory' })).not.toBeInTheDocument()
        expect(screen.getByRole('button', { name: `History for ${TEE_LABEL}` })).toBeInTheDocument()
    })

    it('offers the audit tab only to people holding the audit permission', async () => {
        const { unmount } = renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW) })
        await screen.findByText(TEE_LABEL)
        expect(screen.queryByRole('link', { name: 'Stock audits' })).not.toBeInTheDocument()
        unmount()

        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW, Permission.INVENTORY_AUDIT) })
        expect(await screen.findByRole('link', { name: 'Stock audits' })).toBeInTheDocument()
    })

    it('adjusts stock through the dialog and confirms it', async () => {
        adjustStock.mockResolvedValue(SNAPSHOT)
        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW, Permission.STOCK_ADJUST) })
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: `Adjust stock for ${TEE_LABEL}` }))
        const dialog = await screen.findByRole('dialog', { name: `Adjust stock: ${TEE_LABEL}` })
        await user.type(within(dialog).getByLabelText('Change in quantity'), '-3')
        await user.type(within(dialog).getByLabelText('Reason'), 'cycle count')
        await user.click(within(dialog).getByRole('button', { name: 'Save adjustment' }))

        await waitFor(() => expect(adjustStock).toHaveBeenCalledWith(7, { delta: -3, reason: 'cycle count' }))
        expect(await screen.findByText('Stock adjusted.')).toBeInTheDocument()
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('does not send an invalid adjustment and shows what is wrong', async () => {
        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW, Permission.STOCK_ADJUST) })
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: `Adjust stock for ${TEE_LABEL}` }))
        const dialog = await screen.findByRole('dialog')
        await user.click(within(dialog).getByRole('button', { name: 'Save adjustment' }))

        expect(await within(dialog).findByText('Enter a whole number, like 5 or -3')).toBeInTheDocument()
        expect(within(dialog).getByText('Give a reason')).toBeInTheDocument()
        expect(adjustStock).not.toHaveBeenCalled()
    })

    it('shows the server message when an adjustment is refused', async () => {
        adjustStock.mockRejectedValue(new ApiError(409, 'INSUFFICIENT_STOCK', 'Adjustment of -9 would reduce on-hand (10) below reserved (2)'))
        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW, Permission.STOCK_ADJUST) })
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: `Adjust stock for ${TEE_LABEL}` }))
        const dialog = await screen.findByRole('dialog')
        await user.type(within(dialog).getByLabelText('Change in quantity'), '-9')
        await user.type(within(dialog).getByLabelText('Reason'), 'recount')
        await user.click(within(dialog).getByRole('button', { name: 'Save adjustment' }))

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('below reserved (2)')
        expect(screen.getByRole('dialog')).toBeInTheDocument()
    })

    it('records a receipt with the optional details left blank as nulls', async () => {
        receiveStock.mockResolvedValue(SNAPSHOT)
        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW, Permission.STOCK_ADJUST) })
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: `Receive stock for ${TEE_LABEL}` }))
        const dialog = await screen.findByRole('dialog')
        await user.type(within(dialog).getByLabelText('Quantity received'), '25')
        await user.click(within(dialog).getByRole('button', { name: 'Record receipt' }))

        await waitFor(() =>
            expect(receiveStock).toHaveBeenCalledWith(7, {
                quantity: 25,
                unitCost: null,
                supplierName: null,
                supplierReference: null,
                notes: null,
            }),
        )
        expect(await screen.findByText('Receipt recorded.')).toBeInTheDocument()
    })

    it('marks stock as damaged and changes the threshold', async () => {
        damageStock.mockResolvedValue(SNAPSHOT)
        updateThreshold.mockResolvedValue(SNAPSHOT)
        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW, Permission.STOCK_ADJUST) })
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: `Mark ${TEE_LABEL} damaged` }))
        let dialog = await screen.findByRole('dialog')
        await user.type(within(dialog).getByLabelText('Damaged quantity'), '2')
        await user.type(within(dialog).getByLabelText('Reason'), 'water damage')
        await user.click(within(dialog).getByRole('button', { name: 'Mark as damaged' }))
        await waitFor(() => expect(damageStock).toHaveBeenCalledWith(7, { quantity: 2, reason: 'water damage' }))
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

        await user.click(screen.getByRole('button', { name: `Change threshold for ${TEE_LABEL}` }))
        dialog = await screen.findByRole('dialog')
        const field = within(dialog).getByLabelText('Alert when available stock is at or below')
        expect(field).toHaveValue('5')
        await user.clear(field)
        await user.type(field, '12')
        await user.click(within(dialog).getByRole('button', { name: 'Save threshold' }))
        await waitFor(() => expect(updateThreshold).toHaveBeenCalledWith(7, 12))
    })

    it('searches by text and filters to low stock', async () => {
        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW) })
        const user = userEvent.setup()
        await screen.findByText(TEE_LABEL)

        await user.type(screen.getByLabelText('Search by SKU or product name'), 'hood')
        await user.click(screen.getByRole('button', { name: 'Search' }))
        await waitFor(() => expect(listStock).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'hood', page: 0 })))

        await user.click(screen.getByLabelText('Low stock only'))
        await waitFor(() => expect(listStock).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'hood', lowStockOnly: true })))
    })

    it('opens the ledger history of a variant', async () => {
        stockMovements.mockResolvedValue(page([MOVEMENT]))
        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW) })
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: `History for ${TEE_LABEL}` }))

        const dialog = await screen.findByRole('dialog', { name: `History: ${TEE_LABEL}` })
        expect(await within(dialog).findByText('Goods receipt')).toBeInTheDocument()
        expect(within(dialog).getByText('Goods receipt RCV-1')).toBeInTheDocument()
        expect(stockMovements).toHaveBeenCalledWith(7, 0)
    })

    it('says so when nothing matches, and offers a retry when loading fails', async () => {
        listStock.mockResolvedValueOnce(page([]))
        const { unmount } = renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW) })
        expect(await screen.findByText(/No stock rows yet/)).toBeInTheDocument()
        unmount()

        listStock.mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', 'An unexpected error occurred'))
        renderWithProviders(<StockDashboardPage />, { state: staff(Permission.STOCK_VIEW) })
        expect(await screen.findByRole('alert')).toHaveTextContent('An unexpected error occurred')
        expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    })
})