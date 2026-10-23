import { describe, expect, it } from 'vitest'
import type { PublicCategory } from '../../types/catalog'
import { orderCategories } from './categoryTree'

function category(id: string, parentId: string | null): PublicCategory {
    return { id, parentId, name: id, slug: id.toLowerCase(), depth: 1 }
}

describe('orderCategories', () => {
    it('puts every parent directly before its children, with the nesting level', () => {
        const rows = orderCategories([category('B', null), category('A1a', 'A1'), category('A', null), category('A1', 'A')])

        expect(rows.map((row) => row.category.id)).toEqual(['B', 'A', 'A1', 'A1a'])
        expect(rows.map((row) => row.level)).toEqual([0, 0, 1, 2])
    })

    it('shows a category whose parent is not listed at the top level', () => {
        const rows = orderCategories([category('A', null), category('C', 'hidden-parent')])

        expect(rows.map((row) => [row.category.id, row.level])).toEqual([
            ['A', 0],
            ['C', 0],
        ])
    })

    it('handles an empty list', () => {
        expect(orderCategories([])).toEqual([])
    })
})