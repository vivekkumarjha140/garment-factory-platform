import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cartApi } from '../../api/cart'
import { customerApi } from '../../api/customer'
import { ApiError } from '../../api/errors'
import { ordersApi } from '../../api/orders'
import { cartLine, cartOf } from '../../test/cartFixtures'
import { orderOf } from '../../test/orderFixtures'
import { renderWithProviders } from '../../test/utils'
import type { Address } from '../../types/customer'
import { CheckoutPage } from './CheckoutPage'
import { payOnline } from './payOnline'

vi.mock('../../api/cart', () => ({ cartApi: { get: vi.fn() } }))
vi.mock('../../api/customer', () => ({ customerApi: { addresses: vi.fn() } }))
vi.mock('../../api/orders', () => ({ ordersApi: { place: vi.fn() } }))
vi.mock('./payOnline', () => ({ payOnline: vi.fn() }))

const getCart = vi.mocked(cartApi.get)
const place = vi.mocked(ordersApi.place)
const pay = vi.mocked(payOnline)

const ADDRESS: Address = {
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
}
const NCR = { zoneName: 'Delhi NCR', freeShippingApplied: false, estimatedDaysMin: 1, estimatedDaysMax: 3 }
const readyCart = () =>
    cartOf([cartLine()], { shipping: NCR, shippingAddressId: 'a1', totals: { shippingCalculated: true, shippingCharge: 50, total: 1100 } })

function renderCheckout() {
    return renderWithProviders(
        <Routes>
            <Route path="/checkout" element={<CheckoutPage />} />
            <Route path="/orders/:orderNumber/success" element={<p>success page</p>} />
            <Route path="/account/orders/:orderNumber" element={<p>order page</p>} />
        </Routes>,
        { route: '/checkout' },
    )
}

beforeEach(() => {
    vi.clearAllMocks()
    getCart.mockResolvedValue(readyCart())
    vi.mocked(customerApi.addresses).mockResolvedValue([ADDRESS])
})

describe('CheckoutPage', () => {
    it('shows delivery, the payment choices and the total on the button', async () => {
        renderCheckout()

        expect(await screen.findByText(/Shipping: Delhi NCR · 1–3 days · ₹50/)).toBeInTheDocument()
        expect(screen.getByRole('radio', { name: /pay online/i })).toBeChecked()
        expect(screen.getByRole('radio', { name: /cash on delivery/i })).not.toBeChecked()
        expect(screen.getByRole('button', { name: 'Place order & pay ₹1,100' })).toBeEnabled()
    })

    it('does not let you place an order while an item needs attention', async () => {
        getCart.mockResolvedValue(cartOf([cartLine({ issue: 'OUT_OF_STOCK' })], { shipping: NCR, shippingAddressId: 'a1', totals: { shippingCalculated: true } }))
        renderCheckout()

        expect(await screen.findByRole('button', { name: /place order/i })).toBeDisabled()
        expect(screen.getByRole('link', { name: 'Review your cart' })).toHaveAttribute('href', '/cart')
    })

    it('places a cash-on-delivery order and goes to the success page without opening the payment window', async () => {
        place.mockResolvedValue(orderOf({ paymentMethod: 'COD', orderStatus: 'CONFIRMED', paymentStatus: 'COD_PENDING' }))
        renderCheckout()

        await userEvent.click(await screen.findByRole('radio', { name: /cash on delivery/i }))
        await userEvent.click(screen.getByRole('button', { name: /pay ₹1,100 on delivery/i }))

        expect(await screen.findByText('success page')).toBeInTheDocument()
        expect(place).toHaveBeenCalledWith({ shippingAddressId: 'a1', billingAddressId: null, paymentMethod: 'COD' }, expect.any(String))
        expect(pay).not.toHaveBeenCalled()
    })

    it('places an online order, opens payment, and goes to the success page once paid', async () => {
        place.mockResolvedValue(orderOf())
        pay.mockResolvedValue({ kind: 'submitted' })
        renderCheckout()

        await userEvent.click(await screen.findByRole('button', { name: /place order & pay/i }))

        expect(await screen.findByText('success page')).toBeInTheDocument()
        expect(pay).toHaveBeenCalledWith('GF-2026-000001')
    })

    it('sends the customer to the order page when the payment window is closed', async () => {
        place.mockResolvedValue(orderOf())
        pay.mockResolvedValue({ kind: 'dismissed' })
        renderCheckout()

        await userEvent.click(await screen.findByRole('button', { name: /place order & pay/i }))

        expect(await screen.findByText('order page')).toBeInTheDocument()
    })

    it('shows why an order was refused, and reuses the key only for the same request', async () => {
        place.mockRejectedValueOnce(new ApiError(422, 'COD_LIMIT_EXCEEDED', 'Cash on delivery is available for orders up to Rs. 5000.00. Please pay online.'))
        place.mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server.'))
        place.mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server.'))
        renderCheckout()

        await userEvent.click(await screen.findByRole('radio', { name: /cash on delivery/i }))
        await userEvent.click(screen.getByRole('button', { name: /pay ₹1,100 on delivery/i }))
        expect(await screen.findByRole('alert')).toHaveTextContent('Please pay online.')

        await userEvent.click(screen.getByRole('button', { name: /pay ₹1,100 on delivery/i }))
        await waitFor(() => expect(place).toHaveBeenCalledTimes(2))
        await userEvent.click(screen.getByRole('radio', { name: /pay online/i }))
        await userEvent.click(screen.getByRole('button', { name: /place order & pay/i }))
        await waitFor(() => expect(place).toHaveBeenCalledTimes(3))

        const keys = place.mock.calls.map((call) => call[1])
        expect(keys[1]).toBe(keys[0])
        expect(keys[2]).not.toBe(keys[0])
    })

    it('says so when the cart is empty', async () => {
        getCart.mockResolvedValue(cartOf([]))
        renderCheckout()

        expect(await screen.findByText('Your cart is empty.')).toBeInTheDocument()
    })
})