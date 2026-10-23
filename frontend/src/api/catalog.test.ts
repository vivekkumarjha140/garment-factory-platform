import { describe, expect, it } from 'vitest'
import { buildListRequest } from './catalog'
import { apiClient } from './client'

function urlOf(query: Parameters<typeof buildListRequest>[0]): URL {
    return new URL(apiClient.getUri(buildListRequest(query)), 'http://localhost')
}

const BASE = { sizes: [], colors: [], customizable: false, sort: 'newest' as const, page: 0, size: 12 }

describe('buildListRequest', () => {
    it('sends several sizes and colours as repeated parameters, as the backend binds them', () => {
        const url = urlOf({ ...BASE, sizes: ['M', 'L'], colors: ['Navy Blue'] })

        expect(url.pathname).toBe('/api/v1/catalog/products')
        expect(url.searchParams.getAll('sizes')).toEqual(['M', 'L'])
        expect(url.searchParams.getAll('colors')).toEqual(['Navy Blue'])
    })

    it('leaves unset filters and the default sort out of the URL', () => {
        const url = urlOf(BASE)

        for (const name of ['q', 'category', 'brand', 'minPrice', 'maxPrice', 'sizes', 'colors', 'customizable', 'sort']) {
            expect(url.searchParams.has(name), name).toBe(false)
        }
        expect(url.searchParams.get('page')).toBe('0')
        expect(url.searchParams.get('size')).toBe('12')
    })

    it('sends the chosen filters, sort order and price range', () => {
        const url = urlOf({
            ...BASE,
            q: 'polo',
            category: 't-shirts',
            brand: 'b1',
            minPrice: 100,
            maxPrice: 900,
            customizable: true,
            sort: 'price_asc',
            page: 2,
        })

        expect(Object.fromEntries(url.searchParams)).toEqual({
            q: 'polo',
            category: 't-shirts',
            brand: 'b1',
            minPrice: '100',
            maxPrice: '900',
            customizable: 'true',
            sort: 'price_asc',
            page: '2',
            size: '12',
        })
    })
})