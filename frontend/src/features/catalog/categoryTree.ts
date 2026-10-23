import type { PublicCategory } from '../../types/catalog'

export interface CategoryRow {
    category: PublicCategory
    /** Zero for a top-level category. */
    level: number
}

/**
 * Puts the flat category list into display order: every parent directly followed by its children.
 * A category whose parent is not in the list (for example a hidden parent) is shown at the top level.
 */
export function orderCategories(categories: PublicCategory[]): CategoryRow[] {
    const known = new Set(categories.map((category) => category.id))
    const childrenOf = new Map<string | null, PublicCategory[]>()
    for (const category of categories) {
        const key = category.parentId !== null && known.has(category.parentId) ? category.parentId : null
        const siblings = childrenOf.get(key) ?? []
        siblings.push(category)
        childrenOf.set(key, siblings)
    }

    const rows: CategoryRow[] = []
    const visit = (parent: string | null, level: number) => {
        for (const category of childrenOf.get(parent) ?? []) {
            rows.push({ category, level })
            visit(category.id, level + 1)
        }
    }
    visit(null, 0)
    return rows
}