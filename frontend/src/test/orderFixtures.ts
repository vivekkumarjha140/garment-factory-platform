import type {AdminOrder, AdminOrderSummary, Order, OrderSummary} from '../types/order'

const ADDRESS = {
    recipientName: 'Test User',
    phone: '9876543210',
    line1: '12 MG Road',
    line2: null,
    city: 'Gurugram',
    state: 'Haryana',
    pincode: '122001',
}

/** An online order that is waiting for payment, unless overridden. */
export function orderOf(overrides: Partial<Order> = {}): Order {
    return {
        orderNumber: 'GF-2026-000001',
        orderStatus: 'PENDING_PAYMENT',
        paymentStatus: 'PENDING',
        productionStatus: 'NOT_REQUIRED',
        shipmentStatus: 'NOT_SHIPPED',
        paymentMethod: 'ONLINE',
        placedAt: '2026-10-26T10:00:00Z',
        paymentDueAt: '2099-01-01T00:00:00Z',
        shippingAddress: ADDRESS,
        billingAddress: ADDRESS,
        couponCode: null,
        subtotal: 1000,
        discountTotal: 0,
        shippingTotal: 50,
        taxTotal: 50,
        grandTotal: 1100,
        amountPaid: 0,
        items: [
            {
                productName: 'Classic Tee',
                sku: 'TEE-M-NAVY',
                size: 'M',
                color: 'Navy',
                quantity: 2,
                unitPrice: 500,
                discountAmount: 0,
                taxRate: 5,
                taxAmount: 50,
                lineTotal: 1050,
            },
        ],
        history: [
            { dimension: 'ORDER', fromStatus: null, toStatus: 'PENDING_PAYMENT', reason: 'Order placed', at: '2026-10-26T10:00:00Z' },
            { dimension: 'PAYMENT', fromStatus: null, toStatus: 'PENDING', reason: 'Order placed', at: '2026-10-26T10:00:00Z' },
            { dimension: 'PRODUCTION', fromStatus: null, toStatus: 'NOT_REQUIRED', reason: 'Order placed', at: '2026-10-26T10:00:00Z' },
            { dimension: 'SHIPMENT', fromStatus: null, toStatus: 'NOT_SHIPPED', reason: 'Order placed', at: '2026-10-26T10:00:00Z' },
        ],
        ...overrides,
    }
}

export function paidOrder(overrides: Partial<Order> = {}): Order {
    return orderOf({ orderStatus: 'CONFIRMED', paymentStatus: 'PAID', amountPaid: 1100, ...overrides })
}

export function summaryOf(overrides: Partial<OrderSummary> = {}): OrderSummary {
    return {
        orderNumber: 'GF-2026-000001',
        orderStatus: 'CONFIRMED',
        paymentStatus: 'PAID',
        shipmentStatus: 'NOT_SHIPPED',
        paymentMethod: 'ONLINE',
        placedAt: '2026-10-26T10:00:00Z',
        grandTotal: 1100,
        itemCount: 2,
        firstItemName: 'Classic Tee',
        ...overrides,
    }
}
export function adminOrderOf(overrides: Partial<Order> = {}, invoiceNumber: string | null = null): AdminOrder {
    return {
        order: orderOf(overrides),
        customer: { name: 'Asha Verma', email: 'asha@example.com', phone: '9876543210' },
        invoiceNumber,
    }
}

export function adminSummaryOf(overrides: Partial<AdminOrderSummary> = {}): AdminOrderSummary {
    return { ...summaryOf(), customerName: 'Asha Verma', customerEmail: 'asha@example.com', ...overrides }
}