import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { catalogApi } from '../../api/catalog'
import { ApiError } from '../../api/errors'
import { card, pageOf } from '../../test/catalogFixtures'
import { renderWithProviders } from '../../test/utils'
import { ProductListPage } from './ProductListPage'

vi.mock('../../api/catalog', () => ({
    catalogApi: { categories: vi.fn(), brands: vi.fn(), facets: vi.fn(), list: vi.fn(), product: vi.fn() },
}))

const categories = vi.mocked(catalogApi.categories)
const brands = vi.mocked(catalogApi.brands)
const facets = vi.mocked(catalogApi.facets)
const list = vi.mocked(catalogApi.list)

function renderPage(route = '/shop') {
    return renderWithProviders(
        <Routes>
            <Route path="/shop" element={<ProductListPage />} />
        </Routes>,
        { route },
    )
}

beforeEach(() => {
    vi.clearAllMocks()
    categories.mockResolvedValue([
        { id: 'c1', parentId: null, name: 'T-Shirts', slug: 't-shirts', depth: 1 },
        { id: 'c2', parentId: 'c1', name: 'Polos', slug: 'polos', depth: 2 },
    ])
    brands.mockResolvedValue([{ id: 'b1', name: 'Acme' }])
    facets.mockResolvedValue({ sizes: ['S', 'M', 'L'], colors: [{ name: 'Navy', hex: '#001F3F' }], minPrice: 299, maxPrice: 999 })
    list.mockResolvedValue(
        pageOf([
            card({ id: 'p1', name: 'Classic Tee', slug: 'classic-tee', fromPrice: 499, mrp: 699 }),
            card({ id: 'p2', name: 'Polo Shirt', slug: 'polo-shirt', fromPrice: 799, mrp: 799 }),
        ]),
    )
})

describe('ProductListPage', () => {
    it('lists the products with price, discount and a link to each one', async () => {
        renderPage()

        const link = await screen.findByRole('link', { name: /Classic Tee/ })
        expect(link).toHaveAttribute('href', '/shop/classic-tee')
        expect(link).toHaveTextContent('₹499')
        expect(link).toHaveTextContent('29% off')
        expect(screen.getByText('2 products')).toBeInTheDocument()
        expect(screen.getByRole('heading', { name: 'Shop', level: 1 })).toBeInTheDocument()
        expect(list).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 12, sort: 'newest', sizes: [] }))
    })

    it('asks for matching products when a size is ticked', async () => {
        renderPage()

        await userEvent.setup().click(await screen.findByRole('checkbox', { name: 'M' }))

        await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sizes: ['M'], page: 0 })))
    })

    it('starts from the filters in the URL', async () => {
        renderPage('/shop?sizes=L&sort=price_asc&page=2&category=polos')

        expect(await screen.findByRole('heading', { name: 'Polos', level: 1 })).toBeInTheDocument()
        expect(await screen.findByRole('checkbox', { name: 'L' })).toBeChecked()
        expect(list).toHaveBeenCalledWith(expect.objectContaining({ sizes: ['L'], sort: 'price_asc', page: 1, category: 'polos' }))
    })

    it('resets sizes when the category changes, because the sizes on offer differ', async () => {
        renderPage('/shop?sizes=M')
        const user = userEvent.setup()
        await screen.findByRole('option', { name: /Polos/ })

        await user.selectOptions(screen.getByLabelText('Category'), 'polos')

        await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ category: 'polos', sizes: [] })))
    })

    it('changes the sort order', async () => {
        renderPage()

        await userEvent.setup().selectOptions(screen.getByLabelText('Sort by'), 'price_asc')

        await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'price_asc', page: 0 })))
    })

    it('searches by text', async () => {
        renderPage()
        const user = userEvent.setup()

        await user.type(screen.getByLabelText('Search products'), 'polo')
        await user.click(screen.getByRole('button', { name: 'Search' }))

        await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'polo' })))
    })

    it('explains an empty result and lets the shopper clear the filters', async () => {
        list.mockResolvedValue(pageOf([]))
        renderPage('/shop?sizes=M')
        const user = userEvent.setup()

        expect(await screen.findByText('No products match these filters.')).toBeInTheDocument()
        await user.click(screen.getAllByRole('button', { name: 'Clear all filters' })[0])

        await waitFor(() => expect(list).toHaveBeenLastCalledWith(expect.objectContaining({ sizes: [] })))
    })

    it('shows the error and recovers when the shopper tries again', async () => {
        list.mockRejectedValueOnce(new ApiError(500, 'SERVER_ERROR', 'The shop is unavailable.'))
        renderPage()

        expect(await screen.findByRole('alert')).toHaveTextContent('The shop is unavailable.')
        await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))

        expect(await screen.findByRole('link', { name: /Classic Tee/ })).toBeInTheDocument()
    })

    it('rejects a price range whose minimum is above the maximum without asking the server', async () => {
        renderPage()
        const user = userEvent.setup()
        await screen.findByRole('link', { name: /Classic Tee/ })
        list.mockClear()

        await user.type(screen.getByLabelText('Min price'), '900')
        await user.type(screen.getByLabelText('Max price'), '100')
        await user.click(screen.getByRole('button', { name: 'Apply price' }))

        expect(await screen.findByText('The minimum price cannot be above the maximum.')).toBeInTheDocument()
        expect(list).not.toHaveBeenCalled()
    })
})