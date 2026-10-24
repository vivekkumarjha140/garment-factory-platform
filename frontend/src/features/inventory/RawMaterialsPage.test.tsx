import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/errors'
import { inventoryApi } from '../../api/inventory'
import { renderWithProviders } from '../../test/utils'
import type { PageResponse, RawMaterial } from '../../types/inventory'
import { Permission } from '../auth/permissions'
import { RawMaterialsPage } from './RawMaterialsPage'

vi.mock('../../api/inventory', () => ({
    inventoryApi: {
        listRawMaterials: vi.fn(),
        createRawMaterial: vi.fn(),
        receiveRawMaterial: vi.fn(),
        adjustRawMaterial: vi.fn(),
        rawMaterialMovements: vi.fn(),
    },
}))

const listRawMaterials = vi.mocked(inventoryApi.listRawMaterials)
const createRawMaterial = vi.mocked(inventoryApi.createRawMaterial)
const receiveRawMaterial = vi.mocked(inventoryApi.receiveRawMaterial)

const JERSEY: RawMaterial = {
    id: 3,
    publicId: 'rm3',
    code: 'FAB-JERSEY',
    name: 'Cotton Jersey',
    category: 'FABRIC',
    uom: 'METER',
    onHand: 120.5,
    reserved: 20,
    available: 100.5,
    lowStockThreshold: 50,
    lowStock: false,
    unitCost: 180,
    active: true,
    version: 1,
}
const INK: RawMaterial = {
    ...JERSEY,
    id: 4,
    publicId: 'rm4',
    code: 'CON-INK',
    name: 'Black Ink',
    category: 'CONSUMABLE',
    uom: 'LITRE',
    onHand: 4,
    reserved: 0,
    available: 4,
    lowStockThreshold: 10,
    lowStock: true,
    unitCost: 950.5,
}

const page = (items: RawMaterial[]): PageResponse<RawMaterial> => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: 1 })

const staff = (...permissions: string[]) => ({
    auth: {
        status: 'authenticated' as const,
        user: { publicId: 'u1', email: 'wanda@example.com', fullName: 'Wanda', roles: ['WAREHOUSE_MANAGER'], permissions },
    },
})

beforeEach(() => {
    vi.clearAllMocks()
    listRawMaterials.mockResolvedValue(page([JERSEY, INK]))
})

describe('RawMaterialsPage', () => {
    it('lists raw materials with their quantities, units and status', async () => {
        renderWithProviders(<RawMaterialsPage />, { state: staff(Permission.STOCK_VIEW) })

        expect(await screen.findByText('Cotton Jersey')).toBeInTheDocument()
        expect(screen.getByText('120.5 METER')).toBeInTheDocument()
        expect(screen.getAllByText('Fabric')).toHaveLength(2)
        expect(screen.getAllByText('Consumable')).toHaveLength(2)
        expect(screen.getByText('In stock')).toBeInTheDocument()
        expect(screen.getByText('Low stock')).toBeInTheDocument()
    })

    it('hides the add and stock-changing buttons from view-only staff', async () => {
        renderWithProviders(<RawMaterialsPage />, { state: staff(Permission.STOCK_VIEW) })

        await screen.findByText('Cotton Jersey')
        expect(screen.queryByRole('button', { name: 'Add raw material' })).not.toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Receive Cotton Jersey' })).not.toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'History for Cotton Jersey' })).toBeInTheDocument()
    })

    it('filters by category and low stock', async () => {
        renderWithProviders(<RawMaterialsPage />, { state: staff(Permission.STOCK_VIEW) })
        const user = userEvent.setup()
        await screen.findByText('Cotton Jersey')

        await user.selectOptions(screen.getByLabelText('Filter by category'), 'FABRIC')
        await waitFor(() => expect(listRawMaterials).toHaveBeenLastCalledWith(expect.objectContaining({ category: 'FABRIC', page: 0 })))

        await user.click(screen.getByLabelText('Low stock only'))
        await waitFor(() => expect(listRawMaterials).toHaveBeenLastCalledWith(expect.objectContaining({ lowStockOnly: true })))
    })

    it('does not create a raw material until the form is valid', async () => {
        renderWithProviders(<RawMaterialsPage />, { state: staff(Permission.STOCK_VIEW, Permission.STOCK_ADJUST) })
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Add raw material' }))
        const dialog = await screen.findByRole('dialog', { name: 'Add raw material' })
        await user.click(within(dialog).getByRole('button', { name: 'Create raw material' }))

        expect(await within(dialog).findByText('Enter a code')).toBeInTheDocument()
        expect(within(dialog).getByText('Enter a name')).toBeInTheDocument()
        expect(within(dialog).getByText('Choose a category', { selector: 'p' })).toBeInTheDocument()
        expect(createRawMaterial).not.toHaveBeenCalled()
    })

    it('creates a raw material and confirms it', async () => {
        createRawMaterial.mockResolvedValue({ ...JERSEY, id: 9, code: 'FAB-DENIM', name: 'Denim' })
        renderWithProviders(<RawMaterialsPage />, { state: staff(Permission.STOCK_VIEW, Permission.STOCK_ADJUST) })
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Add raw material' }))
        const dialog = await screen.findByRole('dialog')
        await user.type(within(dialog).getByLabelText('Code'), 'FAB-DENIM')
        await user.type(within(dialog).getByLabelText('Name'), 'Denim')
        await user.selectOptions(within(dialog).getByLabelText('Category'), 'FABRIC')
        await user.type(within(dialog).getByLabelText('Unit of measure'), 'METER')
        await user.type(within(dialog).getByLabelText('Alert threshold (optional)'), '25')
        await user.type(within(dialog).getByLabelText('Unit cost (optional)'), '210.5')
        await user.click(within(dialog).getByRole('button', { name: 'Create raw material' }))

        await waitFor(() =>
            expect(createRawMaterial).toHaveBeenCalledWith({
                code: 'FAB-DENIM',
                name: 'Denim',
                category: 'FABRIC',
                uom: 'METER',
                lowStockThreshold: 25,
                unitCost: 210.5,
            }),
        )
        expect(await screen.findByText('Raw material created.')).toBeInTheDocument()
    })

    it('shows a duplicate-code conflict from the server inside the form', async () => {
        createRawMaterial.mockRejectedValue(new ApiError(409, 'STOCK_CONFLICT', 'Raw material code already exists: FAB-JERSEY'))
        renderWithProviders(<RawMaterialsPage />, { state: staff(Permission.STOCK_VIEW, Permission.STOCK_ADJUST) })
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Add raw material' }))
        const dialog = await screen.findByRole('dialog')
        await user.type(within(dialog).getByLabelText('Code'), 'FAB-JERSEY')
        await user.type(within(dialog).getByLabelText('Name'), 'Jersey again')
        await user.selectOptions(within(dialog).getByLabelText('Category'), 'FABRIC')
        await user.type(within(dialog).getByLabelText('Unit of measure'), 'METER')
        await user.click(within(dialog).getByRole('button', { name: 'Create raw material' }))

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('already exists')
    })

    it('receives a decimal quantity of a raw material', async () => {
        receiveRawMaterial.mockResolvedValue(JERSEY)
        renderWithProviders(<RawMaterialsPage />, { state: staff(Permission.STOCK_VIEW, Permission.STOCK_ADJUST) })
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Receive Cotton Jersey' }))
        const dialog = await screen.findByRole('dialog', { name: 'Receive stock: Cotton Jersey' })
        await user.type(within(dialog).getByLabelText('Quantity received'), '12.5')
        await user.click(within(dialog).getByRole('button', { name: 'Record receipt' }))

        await waitFor(() =>
            expect(receiveRawMaterial).toHaveBeenCalledWith(3, {
                quantity: 12.5,
                unitCost: null,
                supplierName: null,
                supplierReference: null,
                notes: null,
            }),
        )
        expect(await screen.findByText('Receipt recorded.')).toBeInTheDocument()
    })
})