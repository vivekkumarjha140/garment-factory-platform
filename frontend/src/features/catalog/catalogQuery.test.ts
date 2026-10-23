import { describe, expect, it } from 'vitest'
import { EMPTY_FILTERS, PAGE_SIZE, activeFilterCount, parseFilters, toApiQuery, toSearchParams, type CatalogFilters } from './catalogQuery'

describe('parseFilters', () => {
    it('gives the defaults for a plain shop URL', () => {
        expect(parseFilters(new URLSearchParams(''))).toEqual(EMPTY_FILTERS)
    })

    it('reads every filter from the URL', () => {
        const filters = parseFilters(
            new URLSearchParams('q=polo&category=t-shirts&brand=b1&sizes=M&sizes=L&colors=Navy&minPrice=100&maxPrice=900&customizable=true&sort=price_asc&page=3'),
        )

        expect(filters).toEqual({
            q: 'polo',
            category: 't-shirts',
            brand: 'b1',
            minPrice: '100',
            maxPrice: '900',
            sizes: ['M', 'L'],
            colors: ['Navy'],
            customizable: true,
            sort: 'price_asc',
            page: 3,
        })
    })

    it('falls back to safe values for an unknown sort or a bad page', () => {
        expect(parseFilters(new URLSearchParams('sort=cheapest')).sort).toBe('newest')
        expect(parseFilters(new URLSearchParams('page=abc')).page).toBe(1)
        expect(parseFilters(new URLSearchParams('page=0')).page).toBe(1)
    })
})

describe('toSearchParams', () => {
    it('writes nothing for the defaults', () => {
        expect(toSearchParams(EMPTY_FILTERS).toString()).toBe('')
    })

    it('round-trips through the URL', () => {
        const filters: CatalogFilters = {
            q: 'polo shirt',
            category: 'polos',
            brand: 'b1',
            minPrice: '100',
            maxPrice: '900',
            sizes: ['M', 'L'],
            colors: ['Navy Blue'],
            customizable: true,
            sort: 'rating',
            page: 2,
        }

        expect(parseFilters(toSearchParams(filters))).toEqual(filters)
    })
})

describe('toApiQuery', () => {
    it('turns the one-based page into the API page and adds the page size', () => {
        const query = toApiQuery({ ...EMPTY_FILTERS, page: 3 })

        expect(query.page).toBe(2)
        expect(query.size).toBe(PAGE_SIZE)
    })

    it('sends numeric prices and drops empty or invalid ones', () => {
        expect(toApiQuery({ ...EMPTY_FILTERS, minPrice: '100', maxPrice: '900.5' })).toMatchObject({ minPrice: 100, maxPrice: 900.5 })
        const dropped = toApiQuery({ ...EMPTY_FILTERS, minPrice: '', maxPrice: 'abc' })
        expect(dropped.minPrice).toBeUndefined()
        expect(dropped.maxPrice).toBeUndefined()
        expect(toApiQuery({ ...EMPTY_FILTERS, minPrice: '-5' }).minPrice).toBeUndefined()
    })

    it('leaves empty text filters out', () => {
        const query = toApiQuery(EMPTY_FILTERS)

        expect(query.q).toBeUndefined()
        expect(query.category).toBeUndefined()
        expect(query.brand).toBeUndefined()
    })
})

describe('activeFilterCount', () => {
    it('counts narrowing filters but not search text or sort order', () => {
        expect(activeFilterCount({ ...EMPTY_FILTERS, q: 'polo', sort: 'rating' })).toBe(0)
        expect(
            activeFilterCount({ ...EMPTY_FILTERS, category: 'polos', sizes: ['M', 'L'], minPrice: '100', customizable: true }),
        ).toBe(5)
    })
})