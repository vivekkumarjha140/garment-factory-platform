export type OrderStatus = 'PENDING_PAYMENT' | 'CONFIRMED' | 'PROCESSING' | 'COMPLETED' | 'CANCELLED'
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'COD_PENDING' | 'PARTIALLY_REFUNDED' | 'REFUNDED'
export type ProductionStatus = 'NOT_REQUIRED' | 'PENDING' | 'IN_PRODUCTION' | 'COMPLETED'
export type ShipmentStatus = 'NOT_SHIPPED' | 'PACKED' | 'SHIPPED' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'RETURNED'
export type PaymentMethod = 'ONLINE' | 'COD'

export interface OrderAddress {
    recipientName: string
    phone: string
    line1: string
    line2: string | null
    city: string
    state: string
    pincode: string
}

export interface OrderItem {
    productName: string
    sku: string
    size: string
    color: string
    quantity: number
    unitPrice: number
    discountAmount: number
    taxRate: number
    taxAmount: number
    lineTotal: number
}

export interface StatusChange {
    dimension: 'ORDER' | 'PAYMENT' | 'PRODUCTION' | 'SHIPMENT'
    fromStatus: string | null
    toStatus: string
    reason: string | null
    at: string
}

export interface Order {
    orderNumber: string
    orderStatus: OrderStatus
    paymentStatus: PaymentStatus
    productionStatus: ProductionStatus
    shipmentStatus: ShipmentStatus
    paymentMethod: PaymentMethod
    placedAt: string
    paymentDueAt: string | null
    shippingAddress: OrderAddress
    billingAddress: OrderAddress
    couponCode: string | null
    subtotal: number
    discountTotal: number
    shippingTotal: number
    taxTotal: number
    grandTotal: number
    amountPaid: number
    items: OrderItem[]
    history: StatusChange[]
}

export interface OrderSummary {
    orderNumber: string
    orderStatus: OrderStatus
    paymentStatus: PaymentStatus
    shipmentStatus: ShipmentStatus
    paymentMethod: PaymentMethod
    placedAt: string
    grandTotal: number
    itemCount: number
    firstItemName: string | null
}

export interface PlaceOrderBody {
    shippingAddressId: string
    billingAddressId: string | null
    paymentMethod: PaymentMethod
}

/** Everything the Razorpay Checkout widget needs. keyId is the publishable key. */
export interface InitiatePaymentResponse {
    keyId: string
    razorpayOrderId: string
    amountPaise: number
    currency: string
    orderNumber: string
    customerName: string
    customerEmail: string
    customerPhone: string | null
}

export interface SyncPaymentResponse {
    orderNumber: string
    orderStatus: OrderStatus
    paymentStatus: PaymentStatus
    providerStatus: string
}

export interface AdminOrderSummary extends OrderSummary {
    customerName: string
    customerEmail: string
}

export interface AdminOrder {
    order: Order
    customer: { name: string; email: string; phone: string | null }
    invoiceNumber: string | null
}