import type { PublicVariant } from '../../types/catalog'

const SIZE_ORDER = [
    'XXS', 'XS', 'S', 'M', 'L', 'XL', 'XXL', '3XL', '4XL', '5XL', 'FREE',
    '2-3Y', '4-5Y', '6-7Y', '8-9Y', '10-11Y', '12-13Y',
]

export function sameColor(a: string, b: string): boolean {
    return a.toLowerCase() === b.toLowerCase()
}

/** Distinct sizes in garment order (small to large, then kids' sizes). */
export function sortedSizes(variants: PublicVariant[]): string[] {
    const rank = (size: string) => {
        const index = SIZE_ORDER.indexOf(size)
        return index === -1 ? SIZE_ORDER.length : index
    }
    return [...new Set(variants.map((variant) => variant.size))].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b))
}

/** Distinct colours in the order they first appear. Names that differ only by case are one colour. */
export function uniqueColors(variants: PublicVariant[]): Array<{ name: string; hex: string | null }> {
    const colors: Array<{ name: string; hex: string | null }> = []
    for (const variant of variants) {
        if (!colors.some((color) => sameColor(color.name, variant.colorName))) {
            colors.push({ name: variant.colorName, hex: variant.colorHex })
        }
    }
    return colors
}

export function findVariant(variants: PublicVariant[], size: string | null, color: string | null): PublicVariant | undefined {
    if (size === null || color === null) return undefined
    return variants.find((variant) => variant.size === size && sameColor(variant.colorName, color))
}

/** Is there a variant in this size (and, when a colour is chosen, in that colour too)? */
export function isSizeAvailable(variants: PublicVariant[], size: string, color: string | null): boolean {
    return variants.some((variant) => variant.size === size && (color === null || sameColor(variant.colorName, color)))
}

export function isColorAvailable(variants: PublicVariant[], color: string, size: string | null): boolean {
    return variants.some((variant) => sameColor(variant.colorName, color) && (size === null || variant.size === size))
}

/** The price to show: the chosen variant's, or the lowest ("from") while the choice is incomplete. */
export function displayedPrice(
    variants: PublicVariant[],
    selected: PublicVariant | undefined,
    fromPrice: number,
): { price: number; isFrom: boolean } {
    if (selected) return { price: selected.price, isFrom: false }
    if (variants.length === 0) return { price: fromPrice, isFrom: false }
    const prices = variants.map((variant) => variant.price)
    const lowest = Math.min(...prices)
    return { price: lowest, isFrom: Math.max(...prices) !== lowest }
}