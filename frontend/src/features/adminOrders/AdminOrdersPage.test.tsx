import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { adminOrdersApi } from '../../api/adminOrders'
import { adminSummaryOf } from '../../test/orderFixtures'
import { renderWithProviders } from '../../test/utils'
import { Permission } from '../auth/permissions'
import { AdminOrdersPage } from './AdminOrdersPage'

vi.mock('../../api/adminOrders', () => ({ adminOrdersApi: { list: vi.fn() } }))

const list = vi.mocked(adminOrdersApi.list)
const page = (items: ReturnType<typeof adminSummaryOf>[], pageNumber = 0, totalPages = 1) => ({
    items,
    page: pageNumber,
    size: 20,
    totalElements: items.length,
    totalPages,
})
const staff = {
    auth: {
        status: 'authenticated' as const,
        user: { publicId: 'u1', email: 'd@example.com', fullName: 'Dev', roles: ['DISPATCH_MANAGER'], permissions: [Permission.ORDER_VIEW] },
    },
}

beforeEach(() => vi.clearAllMocks())

describe('AdminOrdersPage', () => {
    it('lists orders with customer, total and status', async () => {
        list.mockResolvedValue(page([adminSummaryOf(), adminSummaryOf({ orderNumber: 'GF-2026-000002', customerName: 'Ravi', customerEmail: 'ravi@example.com', orderStatus: 'PENDING_PAYMENT', paymentStatus: 'PENDING' })]))
        renderWithProviders(<AdminOrdersPage />, { state: staff })

        const link = await screen.findByRole('link', { name: 'GF-2026-000001' })
        expect(link).toHaveAttribute('href', '/admin/orders/GF-2026-000001')
        expect(screen.getByText('asha@example.com')).toBeInTheDocument()
        expect(screen.getByRole('table', { name: 'Orders' }))
            .toHaveTextContent('Awaiting payment')
    })

    it('searches and filters, starting again from the first page', async () => {
        list.mockResolvedValue(page([adminSummaryOf()]))
        renderWithProviders(<AdminOrdersPage />, { state: staff })
        await screen.findByRole('link', { name: 'GF-2026-000001' })

        await userEvent.type(screen.getByLabelText('Search'), 'asha')
        await userEvent.selectOptions(screen.getByLabelText('Order status'), 'CONFIRMED')
        await userEvent.click(screen.getByRole('button', { name: 'Search' }))

        await vi.waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'asha', orderStatus: 'CONFIRMED', page: 0 })))
    })

    it('moves between pages', async () => {
        list.mockResolvedValueOnce(page([adminSummaryOf()], 0, 2)).mockResolvedValue(page([adminSummaryOf({ orderNumber: 'GF-2026-000009' })], 1, 2))
        renderWithProviders(<AdminOrdersPage />, { state: staff })

        await userEvent.click(await screen.findByRole('button', { name: 'Next' }))

        expect(await screen.findByRole('link', { name: 'GF-2026-000009' })).toBeInTheDocument()
        expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
    })

    it('says so when nothing matches', async () => {
        list.mockResolvedValue(page([]))
        renderWithProviders(<AdminOrdersPage />, { state: staff })

        expect(await screen.findByText('No orders match these filters.')).toBeInTheDocument()
    })
})