import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ordersApi } from '../../api/orders'
import { summaryOf } from '../../test/orderFixtures'
import { renderWithProviders } from '../../test/utils'
import { OrdersPage } from './OrdersPage'

vi.mock('../../api/orders', () => ({
    ordersApi: { list: vi.fn() },
}))

const list = vi.mocked(ordersApi.list)

const page = (
    items: ReturnType<typeof summaryOf>[],
    pageNumber = 0,
    totalPages = 1,
) => ({
    items,
    page: pageNumber,
    size: 10,
    totalElements: items.length,
    totalPages,
})

beforeEach(() => vi.clearAllMocks())

describe('OrdersPage', () => {
    it('lists orders with status, date, items and total', async () => {
        list.mockResolvedValue(
            page([
                summaryOf(),
                summaryOf({
                    orderNumber: 'GF-2026-000002',
                    orderStatus: 'PENDING_PAYMENT',
                    paymentStatus: 'PENDING',
                    itemCount: 1,
                }),
            ]),
        )

        renderWithProviders(<OrdersPage />)

        const link = await screen.findByRole('link', {
            name: 'GF-2026-000001',
        })

        expect(link).toHaveAttribute(
            'href',
            '/account/orders/GF-2026-000001',
        )
        expect(screen.getByText('Confirmed')).toBeInTheDocument()
        expect(screen.getByText('Awaiting payment')).toBeInTheDocument()
        expect(
            screen.getByText(/26 Oct 2026 · Classic Tee and more \(2 items\)/),
        ).toBeInTheDocument()
    })

    it('moves between pages', async () => {
        list
            .mockResolvedValueOnce(page([summaryOf()], 0, 2))
            .mockResolvedValue(
                page(
                    [summaryOf({ orderNumber: 'GF-2026-000009' })],
                    1,
                    2,
                ),
            )

        renderWithProviders(<OrdersPage />)

        await userEvent.click(
            await screen.findByRole('button', { name: 'Next' }),
        )

        expect(
            await screen.findByRole('link', { name: 'GF-2026-000009' }),
        ).toBeInTheDocument()
        expect(list).toHaveBeenLastCalledWith(1, 10)
    })

    it('shows an empty state when there are no orders', async () => {
        list.mockResolvedValue(page([]))

        renderWithProviders(<OrdersPage />)

        expect(
            await screen.findByText('You have not placed any orders yet.'),
        ).toBeInTheDocument()
    })
})
