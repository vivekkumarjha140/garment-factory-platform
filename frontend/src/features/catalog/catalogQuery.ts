import type { CatalogQuery } from '../../api/catalog'
import type { SortValue } from '../../types/catalog'

export const PAGE_SIZE = 12

export const SORT_OPTIONS: ReadonlyArray<{ value: SortValue; label: string }> = [
    { value: 'newest', label: 'Newest' },
    { value: 'price_asc', label: 'Price: low to high' },
    { value: 'price_desc', label: 'Price: high to low' },
    { value: 'name', label: 'Name' },
    { value: 'rating', label: 'Top rated' },
]

/** The shop's filter state. It lives in the URL, so a filtered list can be shared and the back button works. */
export interface CatalogFilters {
    q: string
    /** Category slug. */
    category: string
    /** Brand public id. */
    brand: string
    minPrice: string
    maxPrice: string
    sizes: string[]
    colors: string[]
    customizable: boolean
    sort: SortValue
    /** One-based in the URL; the API is zero-based. */
    page: number
}

export const EMPTY_FILTERS: CatalogFilters = {
    q: '',
    category: '',
    brand: '',
    minPrice: '',
    maxPrice: '',
    sizes: [],
    colors: [],
    customizable: false,
    sort: 'newest',
    page: 1,
}

export function parseFilters(params: URLSearchParams): CatalogFilters {
    const sort = SORT_OPTIONS.find((option) => option.value === params.get('sort'))?.value ?? 'newest'
    const page = Number.parseInt(params.get('page') ?? '1', 10)
    return {
        q: (params.get('q') ?? '').trim(),
        category: params.get('category') ?? '',
        brand: params.get('brand') ?? '',
        minPrice: params.get('minPrice') ?? '',
        maxPrice: params.get('maxPrice') ?? '',
        sizes: params.getAll('sizes'),
        colors: params.getAll('colors'),
        customizable: params.get('customizable') === 'true',
        sort,
        page: Number.isFinite(page) && page >= 1 ? page : 1,
    }
}

/** Only non-default values are written, so the plain shop URL stays clean. */
export function toSearchParams(filters: CatalogFilters): URLSearchParams {
    const params = new URLSearchParams()
    if (filters.q) params.set('q', filters.q)
    if (filters.category) params.set('category', filters.category)
    if (filters.brand) params.set('brand', filters.brand)
    if (filters.minPrice) params.set('minPrice', filters.minPrice)
    if (filters.maxPrice) params.set('maxPrice', filters.maxPrice)
    for (const size of filters.sizes) params.append('sizes', size)
    for (const color of filters.colors) params.append('colors', color)
    if (filters.customizable) params.set('customizable', 'true')
    if (filters.sort !== 'newest') params.set('sort', filters.sort)
    if (filters.page > 1) params.set('page', String(filters.page))
    return params
}

function toPrice(value: string): number | undefined {
    if (value.trim() === '') return undefined
    const number = Number(value)
    return Number.isFinite(number) && number >= 0 ? number : undefined
}

export function toApiQuery(filters: CatalogFilters): CatalogQuery {
    return {
        q: filters.q || undefined,
        category: filters.category || undefined,
        brand: filters.brand || undefined,
        minPrice: toPrice(filters.minPrice),
        maxPrice: toPrice(filters.maxPrice),
        sizes: filters.sizes,
        colors: filters.colors,
        customizable: filters.customizable,
        sort: filters.sort,
        page: filters.page - 1,
        size: PAGE_SIZE,
    }
}

/** How many narrowing filters are on (search text and sort order are not counted). */
export function activeFilterCount(filters: CatalogFilters): number {
    return (
        (filters.category ? 1 : 0) +
        (filters.brand ? 1 : 0) +
        (filters.minPrice || filters.maxPrice ? 1 : 0) +
        filters.sizes.length +
        filters.colors.length +
        (filters.customizable ? 1 : 0)
    )
}