import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cartApi } from '../../api/cart'
import { cartLine, cartOf } from '../../test/cartFixtures'
import { renderWithProviders } from '../../test/utils'
import { useAuthStatus } from '../auth/useAuthStatus'
import { CartLink } from './CartLink'

vi.mock('../auth/useAuthStatus', () => ({ useAuthStatus: vi.fn() }))
vi.mock('../../api/cart', () => ({ cartApi: { get: vi.fn() } }))

const getCart = vi.mocked(cartApi.get)
const authStatus = vi.mocked(useAuthStatus)

beforeEach(() => {
    vi.clearAllMocks()
    authStatus.mockReturnValue('authenticated' as never)
})

describe('CartLink', () => {
    it('shows how many units can be bought', async () => {
        getCart.mockResolvedValue(
            cartOf([cartLine({ quantity: 2 }), cartLine({ itemId: 2, quantity: 1 }), cartLine({ itemId: 3, quantity: 4, issue: 'UNAVAILABLE' })]),
        )
        renderWithProviders(<CartLink />)

        const link = await screen.findByRole('link', { name: 'Cart, 3 items' })
        expect(link).toHaveAttribute('href', '/cart')
    })

    it('has no badge for an empty cart', async () => {
        getCart.mockResolvedValue(cartOf([]))
        renderWithProviders(<CartLink />)

        expect(await screen.findByRole('link', { name: 'Cart' })).toBeInTheDocument()
    })

    it('is hidden, and loads nothing, for signed-out visitors', () => {
        authStatus.mockReturnValue('anonymous' as never)
        renderWithProviders(<CartLink />)

        expect(screen.queryByRole('link')).not.toBeInTheDocument()
        expect(getCart).not.toHaveBeenCalled()
    })
})