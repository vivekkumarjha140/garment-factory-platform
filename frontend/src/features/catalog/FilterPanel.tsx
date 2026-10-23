import { useMemo, useState, type FormEvent } from 'react'
import { Button } from '../../components/ui/Button'
import { Checkbox } from '../../components/ui/Checkbox'
import { SelectField } from '../../components/ui/SelectField'
import { TextField } from '../../components/ui/TextField'
import { formatPrice } from '../../lib/format'
import type { ColorFacet, Facets, PublicBrand, PublicCategory } from '../../types/catalog'
import { activeFilterCount, type CatalogFilters } from './catalogQuery'
import { orderCategories } from './categoryTree'

interface FilterPanelProps {
    filters: CatalogFilters
    categories: PublicCategory[]
    brands: PublicBrand[]
    facets: Facets | undefined
    onChange: (patch: Partial<CatalogFilters>) => void
    onClear: () => void
}

function toggle(list: string[], value: string): string[] {
    return list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
}

interface PriceRangeProps {
    min: string
    max: string
    available: { min: number; max: number } | null
    onApply: (min: string, max: string) => void
}

/** Applied with a button, so the list is not reloaded on every keystroke. */
function PriceRange({ min, max, available, onApply }: PriceRangeProps) {
    const [draftMin, setDraftMin] = useState(min)
    const [draftMax, setDraftMax] = useState(max)
    const [error, setError] = useState<string | undefined>()

    function handleSubmit(event: FormEvent) {
        event.preventDefault()
        const low = draftMin.trim()
        const high = draftMax.trim()
        if (low !== '' && high !== '' && Number(low) > Number(high)) {
            setError('The minimum price cannot be above the maximum.')
            return
        }
        setError(undefined)
        onApply(low, high)
    }

    return (
        <form onSubmit={handleSubmit} noValidate aria-label="Price range" className="space-y-2">
            <h3 className="text-sm font-medium">Price (₹)</h3>
            {available && (
                <p className="text-xs text-slate-500 dark:text-slate-400">
                    Prices here run from {formatPrice(available.min)} to {formatPrice(available.max)}.
                </p>
            )}
            <div className="grid grid-cols-2 gap-2">
                <TextField
                    label="Min price"
                    type="number"
                    inputMode="decimal"
                    min={0}
                    step="any"
                    value={draftMin}
                    onChange={(event) => setDraftMin(event.target.value)}
                />
                <TextField
                    label="Max price"
                    type="number"
                    inputMode="decimal"
                    min={0}
                    step="any"
                    value={draftMax}
                    onChange={(event) => setDraftMax(event.target.value)}
                />
            </div>
            {error && (
                <p role="alert" className="text-xs text-red-600 dark:text-red-400">
                    {error}
                </p>
            )}
            <Button type="submit" variant="secondary">
                Apply price
            </Button>
        </form>
    )
}

export function FilterPanel({ filters, categories, brands, facets, onChange, onClear }: FilterPanelProps) {
    const rows = useMemo(() => orderCategories(categories), [categories])

    // Choices that are on but missing from the current category's facets stay visible, so they can be turned off.
    const sizes = useMemo(() => {
        const offered = facets?.sizes ?? []
        return [...offered, ...filters.sizes.filter((size) => !offered.includes(size))]
    }, [facets, filters.sizes])
    const colors = useMemo<ColorFacet[]>(() => {
        const offered = facets?.colors ?? []
        const missing = filters.colors.filter((name) => !offered.some((color) => color.name === name))
        return [...offered, ...missing.map((name) => ({ name, hex: null }))]
    }, [facets, filters.colors])

    const available =
        facets && facets.minPrice !== null && facets.maxPrice !== null ? { min: facets.minPrice, max: facets.maxPrice } : null
    const activeCount = activeFilterCount(filters)

    return (
        <div className="space-y-6">
            <SelectField label="Category" value={filters.category} onChange={(event) => onChange({ category: event.target.value })}>
                <option value="">All categories</option>
                {rows.map(({ category, level }) => (
                    <option key={category.id} value={category.slug}>
                        {'\u00A0\u00A0'.repeat(level)}
                        {category.name}
                    </option>
                ))}
            </SelectField>

            {brands.length > 0 && (
                <SelectField label="Brand" value={filters.brand} onChange={(event) => onChange({ brand: event.target.value })}>
                    <option value="">All brands</option>
                    {brands.map((brand) => (
                        <option key={brand.id} value={brand.id}>
                            {brand.name}
                        </option>
                    ))}
                </SelectField>
            )}

            <PriceRange
                key={`${filters.minPrice}|${filters.maxPrice}`}
                min={filters.minPrice}
                max={filters.maxPrice}
                available={available}
                onApply={(minPrice, maxPrice) => onChange({ minPrice, maxPrice })}
            />

            {sizes.length > 0 && (
                <fieldset className="space-y-2">
                    <legend className="text-sm font-medium">Size</legend>
                    <div className="grid grid-cols-3 gap-x-2 gap-y-1">
                        {sizes.map((size) => (
                            <Checkbox
                                key={size}
                                label={size}
                                checked={filters.sizes.includes(size)}
                                onChange={() => onChange({ sizes: toggle(filters.sizes, size) })}
                            />
                        ))}
                    </div>
                </fieldset>
            )}

            {colors.length > 0 && (
                <fieldset className="space-y-2">
                    <legend className="text-sm font-medium">Colour</legend>
                    <div className="space-y-1">
                        {colors.map((color) => (
                            <Checkbox
                                key={color.name}
                                checked={filters.colors.includes(color.name)}
                                onChange={() => onChange({ colors: toggle(filters.colors, color.name) })}
                                label={
                                    <span className="inline-flex items-center">
                                        <span
                                            aria-hidden="true"
                                            className="mr-2 inline-block h-3 w-3 rounded-full border border-slate-400"
                                            style={{ backgroundColor: color.hex ?? 'transparent' }}
                                        />
                                        {color.name}
                                    </span>
                                }
                            />
                        ))}
                    </div>
                </fieldset>
            )}

            <Checkbox
                label="Custom printing available"
                checked={filters.customizable}
                onChange={(event) => onChange({ customizable: event.target.checked })}
            />

            {activeCount > 0 && (
                <Button variant="secondary" onClick={onClear}>
                    Clear all filters
                </Button>
            )}
        </div>
    )
}