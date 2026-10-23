import { Link } from 'react-router'
import { discountPercent, formatPrice } from '../../lib/format'
import type { ProductCardData } from '../../types/catalog'

export function ProductCard({ product }: { product: ProductCardData }) {
    const discount = discountPercent(product.fromPrice, product.mrp)
    return (
        <li>
            <Link
                to={`/shop/${product.slug}`}
                className="block h-full overflow-hidden rounded-lg border border-slate-200 bg-white hover:border-indigo-400 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 dark:border-slate-800 dark:bg-slate-900"
            >
                <div className="aspect-square bg-slate-100 dark:bg-slate-800">
                    {product.imageUrl ? (
                        <img src={product.imageUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
                    ) : (
                        <div className="flex h-full items-center justify-center text-xs text-slate-500 dark:text-slate-400">
                            No photo yet
                        </div>
                    )}
                </div>
                <div className="space-y-1 p-3">
                    <h3 className="text-sm font-medium">{product.name}</h3>
                    {product.brandName && <p className="text-xs text-slate-500 dark:text-slate-400">{product.brandName}</p>}
                    <p className="text-sm">
                        <span className="font-semibold">{formatPrice(product.fromPrice)}</span>
                        {product.mrp > product.fromPrice && (
                            <s className="ml-2 text-xs text-slate-500 dark:text-slate-400">
                                <span className="sr-only">MRP </span>
                                {formatPrice(product.mrp)}
                            </s>
                        )}
                        {discount > 0 && <span className="ml-2 text-xs font-medium text-green-700 dark:text-green-400">{discount}% off</span>}
                    </p>
                    {product.reviewCount > 0 && (
                        <p className="text-xs text-slate-600 dark:text-slate-300">
                            <span aria-hidden="true">
                                ★ {product.rating.toFixed(1)} ({product.reviewCount})
                            </span>
                            <span className="sr-only">
                                Rated {product.rating.toFixed(1)} out of 5 from {product.reviewCount} reviews
                            </span>
                        </p>
                    )}
                    {product.customizable && <p className="text-xs text-indigo-700 dark:text-indigo-300">Custom printing available</p>}
                </div>
            </Link>
        </li>
    )
}