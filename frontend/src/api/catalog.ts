import type { AxiosRequestConfig } from 'axios'
import type { PageResponse } from '../types/auth'
import type { Facets, ProductCardData, PublicBrand, PublicCategory, PublicProduct, SortValue } from '../types/catalog'
import { call } from './call'
import { apiClient } from './client'

export interface CatalogQuery {
    q?: string
    /** Category slug. */
    category?: string
    /** Brand public id. */
    brand?: string
    minPrice?: number
    maxPrice?: number
    sizes: string[]
    colors: string[]
    /** true = only products that can be custom printed. */
    customizable: boolean
    sort: SortValue
    /** Zero-based, as the API expects. */
    page: number
    size: number
}

/**
 * Several sizes or colours must travel as repeated parameters (sizes=M&sizes=L), which is what the
 * backend binds. Axios would otherwise send sizes[]=M. Unset values are left out of the URL.
 */
export function buildListRequest(query: CatalogQuery): AxiosRequestConfig {
    return {
        url: '/catalog/products',
        params: {
            q: query.q || undefined,
            category: query.category || undefined,
            brand: query.brand || undefined,
            minPrice: query.minPrice,
            maxPrice: query.maxPrice,
            sizes: query.sizes.length > 0 ? query.sizes : undefined,
            colors: query.colors.length > 0 ? query.colors : undefined,
            customizable: query.customizable ? true : undefined,
            sort: query.sort === 'newest' ? undefined : query.sort,
            page: query.page,
            size: query.size,
        },
        paramsSerializer: { indexes: null },
    }
}

export const catalogApi = {
    categories: () => call(apiClient.get<PublicCategory[]>('/catalog/categories')),
    brands: () => call(apiClient.get<PublicBrand[]>('/catalog/brands')),
    facets: (category: string) =>
        call(apiClient.get<Facets>('/catalog/facets', { params: { category: category || undefined } })),
    list: (query: CatalogQuery) => call(apiClient.request<PageResponse<ProductCardData>>(buildListRequest(query))),
    product: (slug: string) => call(apiClient.get<PublicProduct>(`/catalog/products/${encodeURIComponent(slug)}`)),
}