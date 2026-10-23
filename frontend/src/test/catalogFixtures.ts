import type { PageResponse } from '../types/auth'
import type { ProductCardData, PublicProduct } from '../types/catalog'

export function card(overrides: Partial<ProductCardData> = {}): ProductCardData {
    return {
        id: 'p1',
        slug: 'classic-tee',
        name: 'Classic Tee',
        brandName: null,
        categoryName: 'T-Shirts',
        categorySlug: 't-shirts',
        fromPrice: 499,
        mrp: 699,
        customizable: false,
        rating: 0,
        reviewCount: 0,
        imageUrl: null,
        ...overrides,
    }
}

export function pageOf(items: ProductCardData[], overrides: Partial<PageResponse<ProductCardData>> = {}): PageResponse<ProductCardData> {
    return { items, page: 0, size: 12, totalElements: items.length, totalPages: items.length === 0 ? 0 : 1, ...overrides }
}

export function product(overrides: Partial<PublicProduct> = {}): PublicProduct {
    return {
        id: 'p1',
        slug: 'classic-tee',
        name: 'Classic Tee',
        description: 'Soft cotton tee.',
        brandName: 'Acme',
        categoryName: 'T-Shirts',
        categorySlug: 't-shirts',
        fabric: 'Cotton',
        gsm: 180,
        composition: '100% cotton',
        gstRate: 5,
        fromPrice: 499,
        mrp: 699,
        customizable: true,
        rating: 4.5,
        reviewCount: 12,
        variants: [
            { id: 'v1', size: 'M', colorName: 'Navy', colorHex: '#001F3F', price: 499 },
            { id: 'v2', size: 'L', colorName: 'Navy', colorHex: '#001F3F', price: 549 },
            { id: 'v3', size: 'M', colorName: 'Red', colorHex: '#FF0000', price: 499 },
        ],
        priceTiers: [
            { minQty: 10, maxQty: 49, unitPrice: 450 },
            { minQty: 50, maxQty: null, unitPrice: 400 },
        ],
        images: [
            { id: 'i1', url: '/api/v1/catalog/images/i1', altText: 'Front view', primary: true, variantId: null },
            { id: 'i2', url: '/api/v1/catalog/images/i2', altText: null, primary: false, variantId: 'v3' },
        ],
        ...overrides,
    }
}