import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { catalogApi } from '../../api/catalog'
import { toApiError, userMessage } from '../../api/errors'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Pagination } from '../../components/ui/Pagination'
import { SelectField } from '../../components/ui/SelectField'
import { usePageTitle } from '../../lib/usePageTitle'
import {
    EMPTY_FILTERS,
    SORT_OPTIONS,
    activeFilterCount,
    parseFilters,
    toApiQuery,
    toSearchParams,
    type CatalogFilters,
} from './catalogQuery'
import { FilterPanel } from './FilterPanel'
import { ProductCard } from './ProductCard'

const FIVE_MINUTES = 5 * 60_000

function SearchBox({ initial, onSearch }: { initial: string; onSearch: (text: string) => void }) {
    const [draft, setDraft] = useState(initial)
    return (
        <form
            role="search"
            onSubmit={(event) => {
                event.preventDefault()
                onSearch(draft.trim())
            }}
            className="flex gap-2"
        >
            <label htmlFor="shop-search" className="sr-only">
                Search products
            </label>
            <input
                id="shop-search"
                type="search"
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                placeholder="Search product"
                className="block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 dark:border-slate-700 dark:bg-slate-950"
            />
            <Button type="submit">Search</Button>
        </form>
    )
}

function LoadingGrid() {
    return (
        <div role="status" aria-label="Loading products" className="grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: 8 }, (_, index) => (
                <div key={index} className="aspect-[3/4] animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
            ))}
        </div>
    )
}

export function ProductListPage() {
    const [params, setParams] = useSearchParams()
    const filters = useMemo(() => parseFilters(params), [params])
    const query = useMemo(() => toApiQuery(filters), [filters])
    const [panelOpen, setPanelOpen] = useState(false)

    const categories = useQuery({ queryKey: ['catalog', 'categories'], queryFn: () => catalogApi.categories(), staleTime: FIVE_MINUTES })
    const brands = useQuery({ queryKey: ['catalog', 'brands'], queryFn: () => catalogApi.brands(), staleTime: FIVE_MINUTES })
    const facets = useQuery({
        queryKey: ['catalog', 'facets', filters.category],
        queryFn: () => catalogApi.facets(filters.category),
        staleTime: FIVE_MINUTES,
    })
    const products = useQuery({
        queryKey: ['catalog', 'products', query],
        queryFn: () => catalogApi.list(query),
        placeholderData: keepPreviousData,
    })

    const category = categories.data?.find((candidate) => candidate.slug === filters.category)
    const heading = category?.name ?? 'Shop'
    usePageTitle(heading)

    const activeCount = activeFilterCount(filters)
    const data = products.data

    function update(patch: Partial<CatalogFilters>) {
        setParams(toSearchParams({ ...filters, page: 1, ...patch }))
    }

    // Sizes, colours and prices on offer differ per category, so they are reset when the category changes.
    function handlePanelChange(patch: Partial<CatalogFilters>) {
        update('category' in patch ? { ...patch, sizes: [], colors: [], minPrice: '', maxPrice: '' } : patch)
    }

    function clearFilters() {
        update({ ...EMPTY_FILTERS, q: filters.q, sort: filters.sort })
    }

    function changePage(zeroBased: number) {
        update({ page: zeroBased + 1 })
        window.scrollTo({ top: 0 })
    }

    return (
        <div className="space-y-6">
            <div className="space-y-4">
                <h1 className="text-2xl font-semibold">{heading}</h1>
                <div className="max-w-xl">
                    <SearchBox key={filters.q} initial={filters.q} onSearch={(q) => update({ q })} />
                </div>
            </div>

            <div className="lg:grid lg:grid-cols-[16rem_1fr] lg:gap-8">
                <aside aria-label="Filters" className="mb-6 lg:mb-0">
                    <Button
                        variant="secondary"
                        className="mb-4 lg:hidden"
                        aria-expanded={panelOpen}
                        aria-controls="filter-panel"
                        onClick={() => setPanelOpen((open) => !open)}
                    >
                        {panelOpen ? 'Hide filters' : activeCount > 0 ? `Filters (${activeCount})` : 'Filters'}
                    </Button>
                    <div id="filter-panel" className={panelOpen ? 'block' : 'hidden lg:block'}>
                        <FilterPanel
                            filters={filters}
                            categories={categories.data ?? []}
                            brands={brands.data ?? []}
                            facets={facets.data}
                            onChange={handlePanelChange}
                            onClear={clearFilters}
                        />
                    </div>
                </aside>

                <section aria-label="Products" className="space-y-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                        <p aria-live="polite" className="text-sm text-slate-600 dark:text-slate-400">
                            {data ? (data.totalElements === 1 ? '1 product' : `${data.totalElements} products`) : '\u00A0'}
                        </p>
                        <div className="w-48">
                            <SelectField
                                label="Sort by"
                                value={filters.sort}
                                onChange={(event) => update({ sort: event.target.value as CatalogFilters['sort'] })}
                            >
                                {SORT_OPTIONS.map((option) => (
                                    <option key={option.value} value={option.value}>
                                        {option.label}
                                    </option>
                                ))}
                            </SelectField>
                        </div>
                    </div>

                    {products.isError && (
                        <Alert variant="error">
                            {userMessage(toApiError(products.error))}{' '}
                            <button type="button" className="underline" onClick={() => void products.refetch()}>
                                Try again
                            </button>
                            {activeCount > 0 && (
                                <>
                                    {' '}
                                    <button type="button" className="underline" onClick={clearFilters}>
                                        Clear filters
                                    </button>
                                </>
                            )}
                        </Alert>
                    )}

                    {products.isPending && <LoadingGrid />}

                    {data && data.items.length === 0 && (
                        <div className="space-y-3 rounded-lg border border-dashed border-slate-300 p-8 text-center dark:border-slate-700">
                            <p className="font-medium">No products match these filters.</p>
                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                Try a different search, or remove some filters to see more.
                            </p>
                            {activeCount > 0 && (
                                <Button variant="secondary" onClick={clearFilters}>
                                    Clear all filters
                                </Button>
                            )}
                        </div>
                    )}

                    {data && data.items.length > 0 && (
                        <ul
                            aria-busy={products.isPlaceholderData}
                            className={`grid grid-cols-2 gap-4 sm:grid-cols-3 xl:grid-cols-4 ${products.isPlaceholderData ? 'opacity-60' : ''}`}
                        >
                            {data.items.map((product) => (
                                <ProductCard key={product.id} product={product} />
                            ))}
                        </ul>
                    )}

                    {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={changePage} />}
                </section>
            </div>
        </div>
    )
}