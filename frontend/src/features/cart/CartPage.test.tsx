import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cartApi } from '../../api/cart'
import { customerApi } from '../../api/customer'
import { ApiError } from '../../api/errors'
import { cartLine, cartOf } from '../../test/cartFixtures'
import { renderWithProviders } from '../../test/utils'
import type { Address } from '../../types/customer'
import { CartPage } from './CartPage'

vi.mock('../../api/cart', () => ({
    cartApi: {
        get: vi.fn(),
        addItem: vi.fn(),
        setQuantity: vi.fn(),
        removeItem: vi.fn(),
        clear: vi.fn(),
        applyCoupon: vi.fn(),
        removeCoupon: vi.fn(),
    },
}))
vi.mock('../../api/customer', () => ({ customerApi: { addresses: vi.fn() } }))

const getCart = vi.mocked(cartApi.get)
const setQuantity = vi.mocked(cartApi.setQuantity)
const removeItem = vi.mocked(cartApi.removeItem)
const clearCart = vi.mocked(cartApi.clear)
const applyCoupon = vi.mocked(cartApi.applyCoupon)
const removeCoupon = vi.mocked(cartApi.removeCoupon)
const loadAddresses = vi.mocked(customerApi.addresses)

function address(overrides: Partial<Address> = {}): Address {
    return {
        id: 'a1',
        label: 'Home',
        recipientName: 'Test User',
        phone: '9876543210',
        line1: '12 MG Road',
        line2: null,
        city: 'Gurugram',
        state: 'Haryana',
        pincode: '122001',
        defaultShipping: true,
        defaultBilling: true,
        ...overrides,
    }
}

const NCR = { zoneName: 'Delhi NCR', freeShippingApplied: false, estimatedDaysMin: 1, estimatedDaysMax: 3 }

beforeEach(() => {
    vi.clearAllMocks()
    getCart.mockResolvedValue(cartOf())
    loadAddresses.mockResolvedValue([])
})

describe('CartPage', () => {
    it('lists the items and the order summary', async () => {
        renderWithProviders(<CartPage />)

        const link = await screen.findByRole('link', { name: 'Classic Tee' })
        expect(link).toHaveAttribute('href', '/shop/classic-tee')
        expect(screen.getByText('Navy · Size M')).toBeInTheDocument()
        expect(screen.getByText('₹500 each · GST 5%')).toBeInTheDocument()

        const summary = screen.getByRole('region', { name: 'Order summary' })
        expect(within(summary).getByText('Subtotal (2 items)')).toBeInTheDocument()
        expect(within(summary).getByText('₹1,000')).toBeInTheDocument()
        expect(within(summary).getByText('Total (excl. shipping)')).toBeInTheDocument()
        expect(within(summary).getByTestId('total')).toHaveTextContent('₹1,050')
    })

    it('says so when the cart is empty', async () => {
        getCart.mockResolvedValue(cartOf([]))
        renderWithProviders(<CartPage />)

        expect(await screen.findByText('Your cart is empty.')).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Browse the shop' })).toHaveAttribute('href', '/shop')
    })

    it('changes the quantity and shows the new totals', async () => {
        setQuantity.mockResolvedValue(cartOf([cartLine({ quantity: 3, lineTotal: 1500, gstAmount: 75 })]))
        renderWithProviders(<CartPage />)
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Increase quantity of Classic Tee' }))

        expect(setQuantity).toHaveBeenCalledWith(1, 3, null)
        await waitFor(() => expect(screen.getByTestId('total')).toHaveTextContent('₹1,575'))
        const stepper = screen.getByRole('group', { name: 'Quantity of Classic Tee' })
        expect(within(stepper).getByText('3')).toBeInTheDocument()
    })

    it('cannot go above the available stock', async () => {
        getCart.mockResolvedValue(cartOf([cartLine({ quantity: 5, availableStock: 5 })]))
        renderWithProviders(<CartPage />)

        expect(await screen.findByRole('button', { name: 'Increase quantity of Classic Tee' })).toBeDisabled()
        expect(screen.getByRole('button', { name: 'Decrease quantity of Classic Tee' })).toBeEnabled()
    })

    it('explains a line that needs fixing and blocks checkout', async () => {
        getCart.mockResolvedValue(cartOf([cartLine({ quantity: 5, availableStock: 3, issue: 'INSUFFICIENT_STOCK' })]))
        renderWithProviders(<CartPage />)

        expect(await screen.findByText('Only 3 left. Please reduce the quantity.')).toBeInTheDocument()
        expect(screen.getByText('Fix the items marked above to continue.')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Proceed to checkout' })).toBeDisabled()
    })

    it('applies a coupon and shows the discount', async () => {
        applyCoupon.mockResolvedValue(
            cartOf([cartLine()], {
                coupon: { code: 'WELCOME10', description: null, type: 'PERCENTAGE', discount: 100, freeShipping: false },
                totals: { discount: 100, taxableAmount: 900, gstAmount: 45, total: 945 },
            }),
        )
        renderWithProviders(<CartPage />)
        const user = userEvent.setup()

        await user.type(await screen.findByLabelText('Coupon code'), ' welcome10 ')
        await user.click(screen.getByRole('button', { name: 'Apply' }))

        expect(applyCoupon).toHaveBeenCalledWith('welcome10', null)
        expect(await screen.findByText('Coupon WELCOME10 applied')).toBeInTheDocument()
        const summary = screen.getByRole('region', { name: 'Order summary' })
        expect(within(summary).getByText('Discount (WELCOME10)')).toBeInTheDocument()
        expect(within(summary).getByText('−₹100')).toBeInTheDocument()
        expect(within(summary).getByTestId('total')).toHaveTextContent('₹945')
    })

    it("shows the server's reason when a coupon is rejected", async () => {
        applyCoupon.mockRejectedValue(new ApiError(422, 'COUPON_INVALID', 'This coupon code is not valid.'))
        renderWithProviders(<CartPage />)
        const user = userEvent.setup()

        await user.type(await screen.findByLabelText('Coupon code'), 'NOPE')
        await user.click(screen.getByRole('button', { name: 'Apply' }))

        expect(await screen.findByText('This coupon code is not valid.')).toBeInTheDocument()
    })

    it('tells the shopper a stored coupon no longer applies and lets them remove it', async () => {
        getCart.mockResolvedValue(cartOf([cartLine()], { couponError: 'This coupon has expired.' }))
        removeCoupon.mockResolvedValue(cartOf())
        renderWithProviders(<CartPage />)
        const user = userEvent.setup()

        expect(await screen.findByText('This coupon has expired.')).toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Remove coupon' }))

        expect(removeCoupon).toHaveBeenCalledWith(null)
    })

    it('removes an item', async () => {
        removeItem.mockResolvedValue(cartOf([]))
        renderWithProviders(<CartPage />)
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Remove Classic Tee' }))

        expect(removeItem).toHaveBeenCalledWith(1, null)
        expect(await screen.findByText('Your cart is empty.')).toBeInTheDocument()
    })

    it('clears the cart only after a confirmation', async () => {
        clearCart.mockResolvedValue(cartOf([]))
        renderWithProviders(<CartPage />)
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Clear cart' }))
        expect(clearCart).not.toHaveBeenCalled()
        expect(screen.getByText('Remove all items from your cart?')).toBeInTheDocument()

        await user.click(screen.getByRole('button', { name: 'Yes, clear cart' }))
        expect(clearCart).toHaveBeenCalledWith(null)
        expect(await screen.findByText('Your cart is empty.')).toBeInTheDocument()
    })

    it('asks for a delivery address when there is none', async () => {
        renderWithProviders(<CartPage />)

        const summary = await screen.findByRole('region', { name: 'Order summary' })
        expect(within(summary).getByText('Add a delivery address')).toBeInTheDocument()
        expect(screen.getByText('Add a delivery address to see shipping charges.')).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Manage addresses' })).toHaveAttribute('href', '/account/addresses')
    })

    it('shows the shipping charge, the total with shipping and the delivery estimate', async () => {
        loadAddresses.mockResolvedValue([address()])
        getCart.mockResolvedValue(
            cartOf([cartLine()], {
                shipping: NCR,
                shippingAddressId: 'a1',
                totals: { shippingCalculated: true, shippingCharge: 50, total: 1100 },
            }),
        )
        renderWithProviders(<CartPage />)

        const summary = await screen.findByRole('region', { name: 'Order summary' })
        expect(within(summary).getByText('Shipping').parentElement).toHaveTextContent('₹50')
        expect(within(summary).getByText('Total')).toBeInTheDocument()
        expect(within(summary).getByTestId('total')).toHaveTextContent('₹1,100')
        expect(within(summary).getByText('Estimated delivery: 1–3 days · Delhi NCR')).toBeInTheDocument()
    })

    it('shows free shipping', async () => {
        getCart.mockResolvedValue(
            cartOf([cartLine()], {
                shipping: { ...NCR, freeShippingApplied: true },
                totals: { shippingCalculated: true, shippingCharge: 0 },
            }),
        )
        renderWithProviders(<CartPage />)

        const summary = await screen.findByRole('region', { name: 'Order summary' })
        expect(within(summary).getByText('Free')).toBeInTheDocument()
    })

    it('explains an address we cannot deliver to', async () => {
        getCart.mockResolvedValue(cartOf([cartLine()], { shippingError: 'We do not deliver to this location yet.' }))
        renderWithProviders(<CartPage />)

        const summary = await screen.findByRole('region', { name: 'Order summary' })
        expect(within(summary).getByText('We do not deliver to this location yet.')).toBeInTheDocument()
    })

    it('reloads the cart for another delivery address', async () => {
        loadAddresses.mockResolvedValue([address(), address({ id: 'a2', label: 'Office', defaultShipping: false })])
        getCart.mockImplementation(async (id) => cartOf([cartLine()], { shippingAddressId: id ?? 'a1' }))
        renderWithProviders(<CartPage />)
        const user = userEvent.setup()

        const picker = await screen.findByLabelText('Deliver to')
        await user.selectOptions(picker, 'a2')

        await waitFor(() => expect(getCart).toHaveBeenLastCalledWith('a2'))
    })

    it('reports a load failure and offers to try again', async () => {
        getCart.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', 'Server is down.'))
        renderWithProviders(<CartPage />)

        expect(await screen.findByText(/Server is down\./)).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
    })

    it('links to checkout once the cart is ready and delivery is priced', async () => {
        getCart.mockResolvedValue(
            cartOf([cartLine()], { shipping: NCR, shippingAddressId: 'a1', totals: { shippingCalculated: true, shippingCharge: 50, total: 1100 } }),
        )
        renderWithProviders(<CartPage />)

        expect(await screen.findByRole('link', { name: 'Proceed to checkout' })).toHaveAttribute('href', '/checkout')
    })
})