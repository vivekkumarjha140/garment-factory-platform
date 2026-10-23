import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { catalogApi } from '../../api/catalog'
import { ApiError } from '../../api/errors'
import { product } from '../../test/catalogFixtures'
import { renderWithProviders } from '../../test/utils'
import { ProductDetailPage } from './ProductDetailPage'

vi.mock('../../api/catalog', () => ({
    catalogApi: { categories: vi.fn(), brands: vi.fn(), facets: vi.fn(), list: vi.fn(), product: vi.fn() },
}))

const loadProduct = vi.mocked(catalogApi.product)

function renderPage() {
    return renderWithProviders(
        <Routes>
            <Route path="/shop/:slug" element={<ProductDetailPage />} />
        </Routes>,
        { route: '/shop/classic-tee' },
    )
}

beforeEach(() => {
    vi.clearAllMocks()
    loadProduct.mockResolvedValue(product())
})

describe('ProductDetailPage', () => {
    it('shows the product, a "from" price, the discount, the details and the bulk prices', async () => {
        renderPage()

        expect(await screen.findByRole('heading', { name: 'Classic Tee', level: 1 })).toBeInTheDocument()
        expect(screen.getByTestId('price')).toHaveTextContent('From ₹499')
        expect(screen.getByText('29% off')).toBeInTheDocument()
        expect(screen.getByText('180 GSM')).toBeInTheDocument()
        expect(screen.getByText('100% cotton')).toBeInTheDocument()
        expect(screen.getByText('This product can be custom printed with your design.')).toBeInTheDocument()
        expect(screen.getByRole('img', { name: 'Front view' })).toHaveAttribute('src', '/api/v1/catalog/images/i1')

        const tiers = screen.getByRole('table', { name: 'Bulk pricing' })
        expect(within(tiers).getByText('10–49')).toBeInTheDocument()
        expect(within(tiers).getByText('₹450')).toBeInTheDocument()
        expect(within(tiers).getByText('50+')).toBeInTheDocument()
    })

    it('shows the exact price once a colour and a size are chosen', async () => {
        renderPage()
        const user = userEvent.setup()
        await screen.findByRole('heading', { name: 'Classic Tee', level: 1 })

        expect(screen.getByText('Choose a colour and a size to see the exact price.')).toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Navy' }))
        await user.click(screen.getByRole('button', { name: 'L' }))

        expect(screen.getByTestId('price')).toHaveTextContent(/^₹549$/)
        expect(screen.getByRole('button', { name: 'L' })).toHaveAttribute('aria-pressed', 'true')
    })

    it('clears the size and says why when the new colour does not come in it, and shows that colour photo', async () => {
        renderPage()
        const user = userEvent.setup()
        await screen.findByRole('heading', { name: 'Classic Tee', level: 1 })

        await user.click(screen.getByRole('button', { name: 'Navy' }))
        await user.click(screen.getByRole('button', { name: 'L' }))
        await user.click(screen.getByRole('button', { name: /^Red/ }))

        expect(screen.getByText('Size L is not available in Red, so the size was cleared.')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: /^L/ })).toHaveAttribute('aria-pressed', 'false')
        expect(screen.getByTestId('price')).toHaveTextContent('From ₹499')
        expect(screen.getByRole('img', { name: 'Classic Tee' })).toHaveAttribute('src', '/api/v1/catalog/images/i2')
    })

    it('starts with the only colour and size selected when there is nothing to choose', async () => {
        loadProduct.mockResolvedValue(
            product({ variants: [{ id: 'v1', size: 'FREE', colorName: 'Black', colorHex: null, price: 399 }], fromPrice: 399, mrp: 399 }),
        )
        renderPage()

        await screen.findByRole('heading', { name: 'Classic Tee', level: 1 })

        expect(screen.getByTestId('price')).toHaveTextContent(/^₹399$/)
        expect(screen.queryByText('Choose a colour and a size to see the exact price.')).not.toBeInTheDocument()
    })

    it('says so when the product does not exist', async () => {
        loadProduct.mockRejectedValue(new ApiError(404, 'PRODUCT_NOT_FOUND', 'Product not found.'))
        renderPage()

        expect(await screen.findByRole('heading', { name: 'We could not find that product' })).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Back to the shop' })).toHaveAttribute('href', '/shop')
    })
})