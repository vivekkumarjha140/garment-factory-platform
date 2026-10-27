import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ordersApi } from '../../api/orders'
import { orderOf, paidOrder } from '../../test/orderFixtures'
import { renderWithProviders } from '../../test/utils'
import { payOnline } from '../checkout/payOnline'
import { OrderDetailPage } from './OrderDetailPage'
import { saveBlob } from './saveBlob'

vi.mock('../../api/orders', () => ({ ordersApi: { get: vi.fn(), cancel: vi.fn(), downloadInvoice: vi.fn() } }))
vi.mock('../checkout/payOnline', () => ({ payOnline: vi.fn() }))
vi.mock('./saveBlob', () => ({ saveBlob: vi.fn() }))

const getOrder = vi.mocked(ordersApi.get)

function renderPage() {
    return renderWithProviders(
        <Routes>
            <Route path="/account/orders/:orderNumber" element={<OrderDetailPage />} />
            <Route path="/orders/:orderNumber/success" element={<p>success page</p>} />
        </Routes>,
        { route: '/account/orders/GF-2026-000001' },
    )
}

beforeEach(() => vi.clearAllMocks())

describe('OrderDetailPage', () => {
    it('shows the items, addresses, totals and tracking', async () => {
        getOrder.mockResolvedValue(paidOrder())
        renderPage()

        expect(await screen.findByRole('heading', { name: 'Order GF-2026-000001' })).toBeInTheDocument()
        expect(screen.getByText('Navy · Size M · SKU TEE-M-NAVY')).toBeInTheDocument()
        expect(screen.getByText('2 × ₹500 · GST 5%')).toBeInTheDocument()
        expect(screen.getByTestId('grand-total')).toHaveTextContent('₹1,100')
        expect(screen.getByText('Shipping address')).toBeInTheDocument()
        expect(within(screen.getByRole('list', { name: 'Order progress' })).getAllByRole('listitem')).toHaveLength(4)
    })

    it('offers to pay an unpaid order, and nothing like an invoice yet', async () => {
        getOrder.mockResolvedValue(orderOf())
        renderPage()

        expect(await screen.findByRole('button', { name: 'Pay now · ₹1,100' })).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Cancel order' })).toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Download invoice' })).not.toBeInTheDocument()
    })

    it('goes to the success page after paying', async () => {
        getOrder.mockResolvedValue(orderOf())
        vi.mocked(payOnline).mockResolvedValue({ kind: 'submitted' })
        renderPage()

        await userEvent.click(await screen.findByRole('button', { name: /pay now/i }))

        expect(await screen.findByText('success page')).toBeInTheDocument()
        expect(payOnline).toHaveBeenCalledWith('GF-2026-000001')
    })

    it('downloads the invoice of a paid order', async () => {
        getOrder.mockResolvedValue(paidOrder())
        const blob = new Blob(['%PDF'])
        vi.mocked(ordersApi.downloadInvoice).mockResolvedValue({ blob, fileName: 'GF-26-27-000001.pdf' })
        renderPage()

        await userEvent.click(await screen.findByRole('button', { name: 'Download invoice' }))

        await vi.waitFor(() => expect(saveBlob).toHaveBeenCalledWith(blob, 'GF-26-27-000001.pdf'))
        expect(screen.queryByRole('button', { name: /pay now/i })).not.toBeInTheDocument()
    })

    it('cancels after a confirmation, with the optional reason', async () => {
        getOrder
            .mockResolvedValueOnce(paidOrder())
            .mockResolvedValue(paidOrder({ orderStatus: 'CANCELLED' }))

        vi.mocked(ordersApi.cancel).mockResolvedValue(
            paidOrder({ orderStatus: 'CANCELLED' }),
        )

        renderPage()

        await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }))
        expect(screen.getByText('You have paid for this order, so it will be refunded in full.')).toBeInTheDocument()
        await userEvent.type(screen.getByLabelText('Reason (optional)'), 'changed my mind')
        await userEvent.click(screen.getByRole('button', { name: 'Yes, cancel order' }))

        expect(await screen.findByText('Cancelled · refund on its way')).toBeInTheDocument()
        expect(ordersApi.cancel).toHaveBeenCalledWith('GF-2026-000001', 'changed my mind')
    })

    it('no longer offers cancellation once the order has shipped', async () => {
        getOrder.mockResolvedValue(paidOrder({ shipmentStatus: 'SHIPPED' }))
        renderPage()

        expect(await screen.findByText('Shipped')).toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
    })
})