import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { adminOrdersApi } from '../../api/adminOrders'
import { ApiError } from '../../api/errors'
import { adminOrderOf } from '../../test/orderFixtures'
import { renderWithProviders } from '../../test/utils'
import { Permission } from '../auth/permissions'
import { saveBlob } from '../orders/saveBlob'
import { AdminOrderDetailPage } from './AdminOrderDetailPage'

vi.mock('../../api/adminOrders', () => ({
    adminOrdersApi: { get: vi.fn(), startProcessing: vi.fn(), pack: vi.fn(), ship: vi.fn(), deliver: vi.fn(), cancel: vi.fn(), downloadInvoice: vi.fn() },
}))
vi.mock('../orders/saveBlob', () => ({ saveBlob: vi.fn() }))

const get = vi.mocked(adminOrdersApi.get)
const staff = (...permissions: string[]) => ({
    auth: {
        status: 'authenticated' as const,
        user: { publicId: 'u1', email: 'd@example.com', fullName: 'Dev', roles: ['DISPATCH_MANAGER'], permissions },
    },
})
const ALL = [Permission.ORDER_VIEW, Permission.ORDER_FULFIL, Permission.ORDER_CANCEL]

function renderPage(state = staff(...ALL)) {
    return renderWithProviders(
        <Routes>
            <Route path="/admin/orders/:orderNumber" element={<AdminOrderDetailPage />} />
        </Routes>,
        { route: '/admin/orders/GF-2026-000001', state },
    )
}

const confirmed = (overrides = {}, invoice: string | null = null) =>
    adminOrderOf({ orderStatus: 'CONFIRMED', paymentStatus: 'PAID', amountPaid: 1100, ...overrides }, invoice)

beforeEach(() => vi.clearAllMocks())

describe('AdminOrderDetailPage', () => {
    it('shows the customer, items, totals and history', async () => {
        get.mockResolvedValue(confirmed())
        renderPage()

        expect(await screen.findByRole('heading', { name: 'Order GF-2026-000001' })).toBeInTheDocument()
        expect(screen.getByText('asha@example.com')).toBeInTheDocument()
        expect(screen.getByText('Navy · Size M · SKU TEE-M-NAVY')).toBeInTheDocument()
        expect(screen.getByTestId('grand-total')).toHaveTextContent('₹1,100')
        expect(screen.getByRole('list', { name: 'Status history' })).toBeInTheDocument()
    })

    it('offers only the next step and moves on after it', async () => {
        get.mockResolvedValue(confirmed())
        vi.mocked(adminOrdersApi.startProcessing).mockResolvedValue(confirmed({ orderStatus: 'PROCESSING' }))
        renderPage()

        expect(screen.queryByRole('button', { name: 'Mark packed' })).not.toBeInTheDocument()
        await userEvent.click(await screen.findByRole('button', { name: 'Start processing' }))

        expect(await screen.findByRole('button', { name: 'Mark packed' })).toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Start processing' })).not.toBeInTheDocument()
        expect(adminOrdersApi.startProcessing).toHaveBeenCalledWith('GF-2026-000001')
    })

    it('does not deliver a cash-on-delivery order until the cash is confirmed', async () => {
        get.mockResolvedValue(adminOrderOf({ paymentMethod: 'COD', orderStatus: 'PROCESSING', paymentStatus: 'COD_PENDING', shipmentStatus: 'SHIPPED' }))
        vi.mocked(adminOrdersApi.deliver).mockResolvedValue(confirmed({ orderStatus: 'COMPLETED', shipmentStatus: 'DELIVERED' }))
        renderPage()

        const deliver = await screen.findByRole('button', { name: 'Mark delivered' })
        expect(deliver).toBeDisabled()
        await userEvent.click(screen.getByLabelText('Cash of ₹1,100 collected from the customer'))
        await userEvent.click(deliver)

        await vi.waitFor(() => expect(adminOrdersApi.deliver).toHaveBeenCalledWith('GF-2026-000001', true))
    })

    it('needs a reason before cancelling', async () => {
        get.mockResolvedValue(confirmed())
        vi.mocked(adminOrdersApi.cancel).mockResolvedValue(confirmed({ orderStatus: 'CANCELLED' }))
        renderPage()

        await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }))
        expect(screen.getByText('The customer has paid, so a full refund will be started automatically.')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Confirm cancellation' })).toBeDisabled()
        await userEvent.type(screen.getByLabelText('Reason (required)'), 'customer called')
        await userEvent.click(screen.getByRole('button', { name: 'Confirm cancellation' }))

        await vi.waitFor(() => expect(adminOrdersApi.cancel).toHaveBeenCalledWith('GF-2026-000001', 'customer called'))
        expect(await screen.findByText('Cancelled · refund on its way')).toBeInTheDocument()
    })

    it('hides the action buttons from view-only staff', async () => {
        get.mockResolvedValue(confirmed())
        renderPage(staff(Permission.ORDER_VIEW))

        await screen.findByRole('heading', { name: 'Order GF-2026-000001' })
        expect(screen.queryByRole('button', { name: 'Start processing' })).not.toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
    })

    it('shows the server message when a step is refused', async () => {
        get.mockResolvedValue(confirmed())
        vi.mocked(adminOrdersApi.startProcessing).mockRejectedValue(new ApiError(409, 'ORDER_ACTION_NOT_ALLOWED', 'This action is not available while the order is cancelled.'))
        renderPage()

        await userEvent.click(await screen.findByRole('button', { name: 'Start processing' }))

        expect(await screen.findByRole('alert')).toHaveTextContent('not available while the order is cancelled')
    })

    it('downloads the invoice of a paid order', async () => {
        get.mockResolvedValue(confirmed({}, 'GF/26-27/000001'))
        const blob = new Blob(['%PDF'])
        vi.mocked(adminOrdersApi.downloadInvoice).mockResolvedValue({ blob, fileName: 'GF-26-27-000001.pdf' })
        renderPage()

        await userEvent.click(await screen.findByRole('button', { name: 'Download invoice' }))

        await vi.waitFor(() => expect(saveBlob).toHaveBeenCalledWith(blob, 'GF-26-27-000001.pdf'))
        expect(screen.getByText('Invoice GF/26-27/000001')).toBeInTheDocument()
    })
})