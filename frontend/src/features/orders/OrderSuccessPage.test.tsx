import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ordersApi } from '../../api/orders'
import { orderOf, paidOrder } from '../../test/orderFixtures'
import { renderWithProviders } from '../../test/utils'
import { OrderSuccessPage } from './OrderSuccessPage'

vi.mock('../../api/orders', () => ({ ordersApi: { get: vi.fn() } }))

const getOrder = vi.mocked(ordersApi.get)

function renderPage(props: { pollMs?: number; maxWaitMs?: number } = {}) {
    return renderWithProviders(
        <Routes>
            <Route path="/orders/:orderNumber/success" element={<OrderSuccessPage {...props} />} />
    </Routes>,
    { route: '/orders/GF-2026-000001/success' },
)
}

    beforeEach(() => vi.clearAllMocks())

    describe('OrderSuccessPage', () => {
        it('confirms an order the server says is paid', async () => {
            getOrder.mockResolvedValue(paidOrder())
            renderPage()

            expect(await screen.findByRole('heading', { name: 'Order confirmed' })).toBeInTheDocument()
            expect(screen.getByRole('link', { name: 'View order' })).toHaveAttribute('href', '/account/orders/GF-2026-000001')
        })

        it('never says confirmed while the payment is still unconfirmed', async () => {
            getOrder.mockResolvedValue(orderOf())
            renderPage()

            expect(await screen.findByRole('heading', { name: 'Confirming your payment…' })).toBeInTheDocument()
            expect(screen.queryByText('Order confirmed')).not.toBeInTheDocument()
        })

        it('switches to confirmed when a later check finds the payment settled', async () => {
            getOrder.mockResolvedValueOnce(orderOf()).mockResolvedValue(paidOrder())
            renderPage({ pollMs: 20 })

            expect(await screen.findByRole('heading', { name: 'Order confirmed' })).toBeInTheDocument()
        })

        it('admits it is taking long, without claiming success, once the wait is over', async () => {
            getOrder.mockResolvedValue(orderOf())
            renderPage({ pollMs: 20, maxWaitMs: 60 })

            expect(await screen.findByText(/taking longer than usual/)).toBeInTheDocument()
            expect(screen.queryByText('Order confirmed')).not.toBeInTheDocument()
        })

        it('tells a cash-on-delivery customer how much to keep ready', async () => {
            getOrder.mockResolvedValue(orderOf({ paymentMethod: 'COD', orderStatus: 'CONFIRMED', paymentStatus: 'COD_PENDING' }))
            renderPage()

            expect(await screen.findByRole('heading', { name: 'Order placed' })).toBeInTheDocument()
            expect(screen.getByText('Please keep ₹1,100 ready to pay when it arrives.')).toBeInTheDocument()
        })

        it('reports a failed payment and a cancelled order', async () => {
            getOrder.mockResolvedValue(orderOf({ paymentStatus: 'FAILED' }))
            const failed = renderPage()
            expect(await screen.findByRole('heading', { name: 'Payment failed' })).toBeInTheDocument()
            failed.unmount()

            getOrder.mockResolvedValue(orderOf({ orderStatus: 'CANCELLED' }))
            renderPage()
            expect(await screen.findByRole('heading', { name: 'Order cancelled' })).toBeInTheDocument()
        })

        it('shows an error when the order cannot be loaded', async () => {
            getOrder.mockRejectedValue(new Error('boom'))
            renderPage()

            expect(await screen.findByRole('alert')).toBeInTheDocument()
        })
    })