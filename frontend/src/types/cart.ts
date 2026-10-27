export interface AddCartItemRequest {
  variantId: string
  quantity: number
}

export type CartLineIssue =
  | 'UNAVAILABLE'
  | 'OUT_OF_STOCK'
  | 'INSUFFICIENT_STOCK'
  | 'QUANTITY_LIMIT'
  | null

export interface CartLine {
  itemId: number
  variantId: string
  productId: number
  productName: string
  productSlug: string | null
  colorName: string | null
  size: string | null
  quantity: number
  availableStock: number
  unitPrice: number
  gstRate: number
  gstAmount: number
  lineTotal: number
  issue: CartLineIssue
}

export interface CartCoupon {
  code: string
  description: string | null
  type: string
  discount: number
  freeShipping: boolean
}

export interface CartTotals {
  totalQuantity: number
  subtotal: number
  discount: number
  taxableAmount: number
  gstAmount: number
  shippingCharge: number | null
  shippingCalculated: boolean
  total: number
}

export interface CartShipping {
  zoneName: string
  freeShippingApplied: boolean
  estimatedDaysMin: number
  estimatedDaysMax: number
}

export interface Cart {
  cartId: number
  lines: CartLine[]
  coupon: CartCoupon | null
  couponError: string | null
  shippingAddressId: string | null
  shipping: CartShipping | null
  shippingError: string | null
  checkoutReady: boolean
  totals: CartTotals
}
