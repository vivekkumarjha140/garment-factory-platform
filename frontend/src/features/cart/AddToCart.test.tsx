import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cartApi } from '../../api/cart'
import { ApiError } from '../../api/errors'
import { cartOf } from '../../test/cartFixtures'
import { renderWithProviders } from '../../test/utils'
import type { PublicVariant } from '../../types/catalog'
import { useAuthStatus } from '../auth/useAuthStatus'
import { AddToCart } from './AddToCart'

vi.mock('../auth/useAuthStatus', () => ({ useAuthStatus: vi.fn() }))
vi.mock('../../api/cart', () => ({
    cartApi: { get: vi.fn(), addItem: vi.fn() },
}))

const addItem = vi.mocked(cartApi.addItem)
const authStatus = vi.mocked(useAuthStatus)

const variant: PublicVariant = { id: 'v1', size: 'M', colorName: 'Navy', colorHex: '#001F3F', price: 499 }

beforeEach(() => {
    vi.clearAllMocks()
    authStatus.mockReturnValue('authenticated' as never)
    addItem.mockResolvedValue(cartOf())
})

describe('AddToCart', () => {
    it('sends signed-out visitors to the sign-in page', () => {
        authStatus.mockReturnValue('anonymous' as never)
        renderWithProviders(<AddToCart variant={variant} productName="Classic Tee" />)

        expect(screen.getByRole('link', { name: 'Sign in to add to cart' })).toHaveAttribute('href', '/login')
        expect(screen.queryByRole('button', { name: 'Add to cart' })).not.toBeInTheDocument()
    })

    it('stays disabled until a colour and a size are chosen', () => {
        renderWithProviders(<AddToCart variant={undefined} productName="Classic Tee" />)

        expect(screen.getByRole('button', { name: 'Add to cart' })).toBeDisabled()
    })

    it('adds the chosen variant with the chosen quantity and links to the cart', async () => {
        renderWithProviders(<AddToCart variant={variant} productName="Classic Tee" />)
        const user = userEvent.setup()

        expect(screen.getByRole('button', { name: 'Decrease quantity of Classic Tee' })).toBeDisabled()
        await user.click(screen.getByRole('button', { name: 'Increase quantity of Classic Tee' }))
        await user.click(screen.getByRole('button', { name: 'Increase quantity of Classic Tee' }))
        await user.click(screen.getByRole('button', { name: 'Add to cart' }))

        expect(addItem).toHaveBeenCalledWith({ variantId: 'v1', quantity: 3 }, null)
        expect(await screen.findByText(/Added to your cart/)).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'View cart' })).toHaveAttribute('href', '/cart')
    })

    it("shows the server's reason when the item cannot be added", async () => {
        addItem.mockRejectedValue(new ApiError(409, 'CART_INSUFFICIENT_STOCK', 'Only 3 unit(s) available.'))
        renderWithProviders(<AddToCart variant={variant} productName="Classic Tee" />)
        const user = userEvent.setup()

        await user.click(screen.getByRole('button', { name: 'Add to cart' }))

        expect(await screen.findByText('Only 3 unit(s) available.')).toBeInTheDocument()
        expect(screen.queryByText(/Added to your cart/)).not.toBeInTheDocument()
    })

    it('waits while the session is still being restored', () => {
        authStatus.mockReturnValue('unknown' as never)
        renderWithProviders(<AddToCart variant={variant} productName="Classic Tee" />)

        expect(screen.getByRole('button', { name: 'Add to cart' })).toBeDisabled()
    })
})