import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import { catalogApi } from '../../api/catalog'
import { toApiError, userMessage } from '../../api/errors'
import { Alert } from '../../components/ui/Alert'
import { discountPercent, formatPrice } from '../../lib/format'
import { usePageTitle } from '../../lib/usePageTitle'
import type { PublicProduct } from '../../types/catalog'
import {
    displayedPrice,
    findVariant,
    isColorAvailable,
    isSizeAvailable,
    sameColor,
    sortedSizes,
    uniqueColors,
} from './variantSelection'

const OPTION_BASE =
    'rounded-md border px-3 py-1.5 text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400'
const OPTION_ON = 'border-indigo-600 bg-indigo-600 text-white'
const OPTION_OFF = 'border-slate-300 hover:border-indigo-400 dark:border-slate-700'
const OPTION_MUTED = 'border-dashed opacity-60'

function DetailSkeleton() {
    return (
        <div role="status" aria-label="Loading product" className="grid gap-8 lg:grid-cols-2">
            <div className="aspect-square animate-pulse rounded-lg bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
            <div className="space-y-3">
                <div className="h-8 w-2/3 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                <div className="h-6 w-1/3 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
                <div className="h-24 animate-pulse rounded bg-slate-200 motion-reduce:animate-none dark:bg-slate-800" />
            </div>
        </div>
    )
}

function ProductView({ product }: { product: PublicProduct }) {
    const colors = useMemo(() => uniqueColors(product.variants), [product.variants])
    const sizes = useMemo(() => sortedSizes(product.variants), [product.variants])

    // With only one colour or size there is nothing to choose, so it starts selected.
    const [color, setColor] = useState<string | null>(colors.length === 1 ? colors[0].name : null)
    const [size, setSize] = useState<string | null>(sizes.length === 1 ? sizes[0] : null)
    const [imageIndex, setImageIndex] = useState(() => Math.max(0, product.images.findIndex((image) => image.primary)))
    const [notice, setNotice] = useState<string | null>(null)

    const selected = findVariant(product.variants, size, color)
    const { price, isFrom } = displayedPrice(product.variants, selected, product.fromPrice)
    const discount = discountPercent(price, product.mrp)
    const image = product.images[imageIndex]
    const incomplete = !selected && product.variants.length > 1

    function chooseColor(name: string) {
        setColor(name)
        if (size !== null && !isSizeAvailable(product.variants, size, name)) {
            setNotice(`Size ${size} is not available in ${name}, so the size was cleared.`)
            setSize(null)
        } else {
            setNotice(null)
        }
        const ids = new Set(product.variants.filter((variant) => sameColor(variant.colorName, name)).map((variant) => variant.id))
        const match = product.images.findIndex((candidate) => candidate.variantId !== null && ids.has(candidate.variantId))
        if (match >= 0) setImageIndex(match)
    }

    function chooseSize(next: string) {
        setSize(next)
        if (color !== null && !isColorAvailable(product.variants, color, next)) {
            setNotice(`${color} is not available in size ${next}, so the colour was cleared.`)
            setColor(null)
        } else {
            setNotice(null)
        }
    }

    return (
        <div className="space-y-6">
            <nav aria-label="Breadcrumb" className="text-sm text-slate-600 dark:text-slate-400">
                <ol className="flex flex-wrap items-center gap-2">
                    <li>
                        <Link to="/shop" className="underline">
                            Shop
                        </Link>
                    </li>
                    <li aria-hidden="true">/</li>
                    <li>
                        <Link to={`/shop?category=${encodeURIComponent(product.categorySlug)}`} className="underline">
                            {product.categoryName}
                        </Link>
                    </li>
                    <li aria-hidden="true">/</li>
                    <li aria-current="page">{product.name}</li>
                </ol>
            </nav>

            <div className="grid gap-8 lg:grid-cols-2">
                <div>
                    <div className="aspect-square overflow-hidden rounded-lg bg-slate-100 dark:bg-slate-800">
                        {image ? (
                            <img src={image.url} alt={image.altText ?? product.name} className="h-full w-full object-cover" />
                        ) : (
                            <div className="flex h-full items-center justify-center text-sm text-slate-500 dark:text-slate-400">
                                No photo yet
                            </div>
                        )}
                    </div>
                    {product.images.length > 1 && (
                        <ul aria-label="Product photos" className="mt-3 flex flex-wrap gap-2">
                            {product.images.map((thumb, index) => (
                                <li key={thumb.id}>
                                    <button
                                        type="button"
                                        aria-label={`Show photo ${index + 1} of ${product.images.length}`}
                                        aria-pressed={index === imageIndex}
                                        onClick={() => setImageIndex(index)}
                                        className={`overflow-hidden rounded-md border-2 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 ${index === imageIndex ? 'border-indigo-600' : 'border-transparent'}`}
                                    >
                                        <img src={thumb.url} alt="" loading="lazy" className="h-16 w-16 object-cover" />
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                <div className="space-y-5">
                    <div className="space-y-1">
                        <h1 className="text-2xl font-semibold">{product.name}</h1>
                        {product.brandName && <p className="text-sm text-slate-600 dark:text-slate-400">{product.brandName}</p>}
                        {product.reviewCount > 0 && (
                            <p className="text-sm">
                                <span aria-hidden="true">
                                    ★ {product.rating.toFixed(1)} ({product.reviewCount})
                                </span>
                                <span className="sr-only">
                                    Rated {product.rating.toFixed(1)} out of 5 from {product.reviewCount} reviews
                                </span>
                            </p>
                        )}
                    </div>

                    <div>
                        <p data-testid="price" className="text-2xl font-semibold">
                            {isFrom && <span className="text-sm font-normal text-slate-500 dark:text-slate-400">From </span>}
                            {formatPrice(price)}
                        </p>
                        {product.mrp > price && (
                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                <s>
                                    <span className="sr-only">MRP </span>
                                    {formatPrice(product.mrp)}
                                </s>
                                {discount > 0 && <span className="ml-2 font-medium text-green-700 dark:text-green-400">{discount}% off</span>}
                            </p>
                        )}
                    </div>

                    {colors.length > 0 && (
                        <fieldset className="space-y-2">
                            <legend className="text-sm font-medium">Colour{color ? `: ${color}` : ''}</legend>
                            <div className="flex flex-wrap gap-2">
                                {colors.map((candidate) => {
                                    const on = color !== null && sameColor(color, candidate.name)
                                    const muted = !isColorAvailable(product.variants, candidate.name, size)
                                    return (
                                        <button
                                            key={candidate.name}
                                            type="button"
                                            aria-pressed={on}
                                            onClick={() => chooseColor(candidate.name)}
                                            className={`${OPTION_BASE} inline-flex items-center ${on ? OPTION_ON : OPTION_OFF} ${muted && !on ? OPTION_MUTED : ''}`}
                                        >
                                            <span
                                                aria-hidden="true"
                                                className="mr-2 inline-block h-3 w-3 rounded-full border border-slate-400"
                                                style={{ backgroundColor: candidate.hex ?? 'transparent' }}
                                            />
                                            {candidate.name}
                                            {muted && !on && <span className="sr-only"> (not available in size {size})</span>}
                                        </button>
                                    )
                                })}
                            </div>
                        </fieldset>
                    )}

                    {sizes.length > 0 && (
                        <fieldset className="space-y-2">
                            <legend className="text-sm font-medium">Size{size ? `: ${size}` : ''}</legend>
                            <div className="flex flex-wrap gap-2">
                                {sizes.map((candidate) => {
                                    const on = size === candidate
                                    const muted = !isSizeAvailable(product.variants, candidate, color)
                                    return (
                                        <button
                                            key={candidate}
                                            type="button"
                                            aria-pressed={on}
                                            onClick={() => chooseSize(candidate)}
                                            className={`${OPTION_BASE} ${on ? OPTION_ON : OPTION_OFF} ${muted && !on ? OPTION_MUTED : ''}`}
                                        >
                                            {candidate}
                                            {muted && !on && <span className="sr-only"> (not available in {color})</span>}
                                        </button>
                                    )
                                })}
                            </div>
                        </fieldset>
                    )}

                    <div aria-live="polite" className="space-y-2">
                        {notice && <Alert variant="info">{notice}</Alert>}
                        {incomplete && !notice && (
                            <p className="text-sm text-slate-600 dark:text-slate-400">Choose a colour and a size to see the exact price.</p>
                        )}
                    </div>

                    {product.customizable && (
                        <p className="rounded-md border border-indigo-200 bg-indigo-50 px-3 py-2 text-sm text-indigo-900 dark:border-indigo-900 dark:bg-indigo-950 dark:text-indigo-200">
                            This product can be custom printed with your design.
                        </p>
                    )}

                    {product.description && <p className="whitespace-pre-line text-sm leading-relaxed">{product.description}</p>}

                    <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm">
                        {product.fabric && (
                            <>
                                <dt className="text-slate-500 dark:text-slate-400">Fabric</dt>
                                <dd>{product.fabric}</dd>
                            </>
                        )}
                        {product.gsm !== null && (
                            <>
                                <dt className="text-slate-500 dark:text-slate-400">Weight</dt>
                                <dd>{product.gsm} GSM</dd>
                            </>
                        )}
                        {product.composition && (
                            <>
                                <dt className="text-slate-500 dark:text-slate-400">Composition</dt>
                                <dd>{product.composition}</dd>
                            </>
                        )}
                    </dl>

                    {product.priceTiers.length > 0 && (
                        <div className="overflow-x-auto">
                            <table className="w-full max-w-sm text-left text-sm">
                                <caption className="pb-2 text-left font-medium">Bulk pricing</caption>
                                <thead className="text-xs text-slate-600 dark:text-slate-400">
                                <tr>
                                    <th scope="col" className="py-1 pr-4 font-medium">
                                        Quantity
                                    </th>
                                    <th scope="col" className="py-1 font-medium">
                                        Price per piece
                                    </th>
                                </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                                {product.priceTiers.map((tier) => (
                                    <tr key={tier.minQty}>
                                        <td className="py-1 pr-4">{tier.maxQty === null ? `${tier.minQty}+` : `${tier.minQty}–${tier.maxQty}`}</td>
                                        <td className="py-1">{formatPrice(tier.unitPrice)}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}

export function ProductDetailPage() {
    const { slug = '' } = useParams()
    const product = useQuery({
        queryKey: ['catalog', 'product', slug],
        queryFn: () => catalogApi.product(slug),
        retry: (failures, error) => toApiError(error).status !== 404 && failures < 1,
    })
    usePageTitle(product.data?.name)

    if (product.isPending) return <DetailSkeleton />

    if (product.isError) {
        const error = toApiError(product.error)
        if (error.status === 404) {
            return (
                <div role="alert" className="space-y-3 py-10 text-center">
                    <h1 className="text-xl font-semibold">We could not find that product</h1>
                    <p className="text-sm text-slate-600 dark:text-slate-400">It may have been removed or is no longer on sale.</p>
                    <Link to="/shop" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                        Back to the shop
                    </Link>
                </div>
            )
        }
        return (
            <Alert variant="error">
                {userMessage(error)}{' '}
                <button type="button" className="underline" onClick={() => void product.refetch()}>
                    Try again
                </button>
            </Alert>
        )
    }

    return <ProductView key={product.data.id} product={product.data} />
}