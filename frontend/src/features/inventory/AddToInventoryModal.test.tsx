import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { inventoryApi } from '../../api/inventory'
import { renderWithProviders } from '../../test/utils'
import type { StockSnapshot, UninitializedVariant } from '../../types/inventory'
import { AddToInventoryModal } from './AddToInventoryModal'

vi.mock('../../api/inventory', () => ({
    inventoryApi: {
        uninitializedVariants: vi.fn(),
        initializeStock: vi.fn(),
        initializeMissing: vi.fn(),
    },
}))

const uninitializedVariants = vi.mocked(inventoryApi.uninitializedVariants)
const initializeStock = vi.mocked(inventoryApi.initializeStock)
const initializeMissing = vi.mocked(inventoryApi.initializeMissing)

const VARIANT: UninitializedVariant = { variantId: 31, sku: 'POLO-L-NVY', productName: 'Polo', size: 'L', colorName: 'Navy' }

beforeEach(() => {
    vi.clearAllMocks()
    uninitializedVariants.mockResolvedValue([VARIANT])
})

describe('AddToInventoryModal', () => {
    it('lists variants that have no stock row yet', async () => {
        renderWithProviders(<AddToInventoryModal onClose={() => {}} />)

        expect(await screen.findByText('Polo')).toBeInTheDocument()
        expect(screen.getByText('POLO-L-NVY')).toBeInTheDocument()
    })

    it('adds one variant with the default alert level when the field is empty', async () => {
        initializeStock.mockResolvedValue({} as StockSnapshot)
        renderWithProviders(<AddToInventoryModal onClose={() => {}} />)

        await userEvent.setup().click(await screen.findByRole('button', { name: 'Add POLO-L-NVY to inventory' }))

        await waitFor(() => expect(initializeStock).toHaveBeenCalledWith(31, undefined))
        expect(await screen.findByText('Added POLO-L-NVY to inventory.')).toBeInTheDocument()
    })

    it('sends a custom alert level', async () => {
        initializeStock.mockResolvedValue({} as StockSnapshot)
        renderWithProviders(<AddToInventoryModal onClose={() => {}} />)
        const user = userEvent.setup()

        await user.type(screen.getByLabelText('Alert level for new stock rows (optional)'), '4')
        await user.click(await screen.findByRole('button', { name: 'Add POLO-L-NVY to inventory' }))

        await waitFor(() => expect(initializeStock).toHaveBeenCalledWith(31, 4))
    })

    it('refuses an invalid alert level before calling the server', async () => {
        renderWithProviders(<AddToInventoryModal onClose={() => {}} />)
        const user = userEvent.setup()

        await user.type(screen.getByLabelText('Alert level for new stock rows (optional)'), 'abc')
        await user.click(await screen.findByRole('button', { name: 'Add POLO-L-NVY to inventory' }))

        expect(await screen.findByText('Enter a whole number, 0 or more')).toBeInTheDocument()
        expect(initializeStock).not.toHaveBeenCalled()
    })

    it('creates every missing stock row in one go', async () => {
        initializeMissing.mockResolvedValue({ created: 3 })
        renderWithProviders(<AddToInventoryModal onClose={() => {}} />)

        await userEvent.setup().click(await screen.findByRole('button', { name: 'Add all missing variants' }))

        await waitFor(() => expect(initializeMissing).toHaveBeenCalledWith(undefined))
        expect(await screen.findByText('3 stock rows created.')).toBeInTheDocument()
    })

    it('searches the catalog for variants without stock', async () => {
        renderWithProviders(<AddToInventoryModal onClose={() => {}} />)
        const user = userEvent.setup()
        await screen.findByText('Polo')

        await user.type(screen.getByLabelText('Find a variant without stock'), 'polo')
        await user.click(screen.getByRole('button', { name: 'Find' }))

        await waitFor(() => expect(uninitializedVariants).toHaveBeenLastCalledWith('polo'))
    })
})