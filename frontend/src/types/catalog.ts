export type SortValue = 'newest' | 'price_asc' | 'price_desc' | 'name' | 'rating'

export interface PublicCategory {
    id: string
    parentId: string | null
    name: string
    slug: string
    depth: number
}

export interface PublicBrand {
    id: string
    name: string
}

/** One product in a listing. fromPrice is the lowest price among its active variants. */
export interface ProductCardData {
    id: string
    slug: string
    name: string
    brandName: string | null
    categoryName: string
    categorySlug: string
    fromPrice: number
    mrp: number
    customizable: boolean
    rating: number
    reviewCount: number
    imageUrl: string | null
}

export interface PublicVariant {
    id: string
    size: string
    colorName: string
    colorHex: string | null
    price: number
}

export interface PublicImage {
    id: string
    url: string
    altText: string | null
    primary: boolean
    /** Public id of the variant this photo shows, or null for a general photo. */
    variantId: string | null
}

export interface PriceTier {
    minQty: number
    maxQty: number | null
    unitPrice: number
}

export interface PublicProduct {
    id: string
    slug: string
    name: string
    description: string | null
    brandName: string | null
    categoryName: string
    categorySlug: string
    fabric: string | null
    gsm: number | null
    composition: string | null
    gstRate: number
    fromPrice: number
    mrp: number
    customizable: boolean
    rating: number
    reviewCount: number
    variants: PublicVariant[]
    priceTiers: PriceTier[]
    images: PublicImage[]
}

export interface ColorFacet {
    name: string
    hex: string | null
}

/** What can be filtered on within the current category. */
export interface Facets {
    sizes: string[]
    colors: ColorFacet[]
    minPrice: number | null
    maxPrice: number | null
}