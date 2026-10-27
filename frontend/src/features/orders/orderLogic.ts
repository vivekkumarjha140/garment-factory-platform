import type { Order, OrderSummary, StatusChange } from '../../types/order'

export type StatusFields = Pick<OrderSummary, 'orderStatus' | 'paymentStatus' | 'paymentMethod' | 'shipmentStatus'>
export type Tone = 'green' | 'amber' | 'red' | 'blue' | 'slate'

/** The one label a customer sees for an order, combining its payment, order and shipment status. */
export function orderBadge(order: StatusFields): { label: string; tone: Tone } {
    if (order.orderStatus === 'CANCELLED') {
        if (order.paymentStatus === 'REFUNDED') return { label: 'Cancelled · refunded', tone: 'slate' }
        if (order.paymentStatus === 'PAID' || order.paymentStatus === 'PARTIALLY_REFUNDED') {
            return { label: 'Cancelled · refund on its way', tone: 'amber' }
        }
        return { label: 'Cancelled', tone: 'slate' }
    }
    switch (order.shipmentStatus) {
        case 'DELIVERED':
            return { label: 'Delivered', tone: 'green' }
        case 'OUT_FOR_DELIVERY':
            return { label: 'Out for delivery', tone: 'blue' }
        case 'SHIPPED':
            return { label: 'Shipped', tone: 'blue' }
        case 'PACKED':
            return { label: 'Packed', tone: 'blue' }
        case 'RETURNED':
            return { label: 'Returned', tone: 'slate' }
        default:
            break
    }
    if (order.orderStatus === 'PENDING_PAYMENT') {
        return order.paymentStatus === 'FAILED' ? { label: 'Payment failed', tone: 'red' } : { label: 'Awaiting payment', tone: 'amber' }
    }
    if (order.orderStatus === 'COMPLETED') return { label: 'Completed', tone: 'green' }
    if (order.orderStatus === 'PROCESSING') return { label: 'Being prepared', tone: 'blue' }
    return { label: order.paymentMethod === 'COD' ? 'Confirmed · pay on delivery' : 'Confirmed', tone: 'green' }
}

/** Mirrors the backend rule: online, unpaid, not expired. The server re-checks on every attempt. */
export function canPay(order: Order, now: number = Date.now()): boolean {
    return (
        order.paymentMethod === 'ONLINE' &&
        order.orderStatus === 'PENDING_PAYMENT' &&
        (order.paymentStatus === 'PENDING' || order.paymentStatus === 'FAILED') &&
        (order.paymentDueAt === null || Date.parse(order.paymentDueAt) > now)
    )
}

export function canCancel(order: Pick<Order, 'orderStatus' | 'shipmentStatus'>): boolean {
    return (order.orderStatus === 'PENDING_PAYMENT' || order.orderStatus === 'CONFIRMED') && order.shipmentStatus === 'NOT_SHIPPED'
}

export function invoiceAvailable(order: Pick<Order, 'orderStatus' | 'paymentStatus'>): boolean {
    return order.paymentStatus === 'PAID' && ['CONFIRMED', 'PROCESSING', 'COMPLETED'].includes(order.orderStatus)
}

export function awaitingPayment(order: Pick<Order, 'orderStatus' | 'paymentStatus' | 'paymentMethod'>): boolean {
    return order.paymentMethod === 'ONLINE' && order.orderStatus === 'PENDING_PAYMENT' && order.paymentStatus === 'PENDING'
}

export type PlacementOutcome = 'confirmed' | 'cod-confirmed' | 'awaiting' | 'failed' | 'cancelled'

/** What the success page may say. Only the server's own order status counts; arriving on the page proves nothing. */
export function placementOutcome(order: Pick<Order, 'orderStatus' | 'paymentStatus' | 'paymentMethod'>): PlacementOutcome {
    if (order.orderStatus === 'CANCELLED') return 'cancelled'
    if (order.paymentMethod === 'COD') return 'cod-confirmed'
    if (order.paymentStatus === 'PAID') return 'confirmed'
    if (order.paymentStatus === 'FAILED') return 'failed'
    return 'awaiting'
}

export interface TrackingStep {
    key: 'placed' | 'confirmed' | 'shipped' | 'delivered'
    label: string
    state: 'done' | 'current' | 'upcoming'
}

export function trackingSteps(order: Pick<Order, 'orderStatus' | 'shipmentStatus'>): TrackingStep[] {
    const shipped = ['SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'RETURNED'].includes(order.shipmentStatus)
    const delivered = order.shipmentStatus === 'DELIVERED' || order.shipmentStatus === 'RETURNED'
    const done = [true, order.orderStatus !== 'PENDING_PAYMENT' && order.orderStatus !== 'CANCELLED', shipped, delivered]
    const steps: Pick<TrackingStep, 'key' | 'label'>[] = [
        { key: 'placed', label: 'Order placed' },
        { key: 'confirmed', label: 'Confirmed' },
        { key: 'shipped', label: 'Shipped' },
        { key: 'delivered', label: 'Delivered' },
    ]
    const firstOpen = done.findIndex((isDone) => !isDone)
    return steps.map((step, index) => ({
        ...step,
        state: done[index] ? 'done' : index === firstOpen ? 'current' : 'upcoming',
    }))
}

const STATUS_TEXT: Record<string, string> = {
    PENDING_PAYMENT: 'Awaiting payment',
    CONFIRMED: 'Order confirmed',
    PROCESSING: 'Being prepared',
    COMPLETED: 'Order completed',
    CANCELLED: 'Order cancelled',
    PENDING: 'Payment pending',
    PAID: 'Payment received',
    FAILED: 'Payment failed',
    COD_PENDING: 'Pay on delivery',
    PARTIALLY_REFUNDED: 'Partly refunded',
    REFUNDED: 'Refunded',
    PACKED: 'Packed',
    SHIPPED: 'Shipped',
    OUT_FOR_DELIVERY: 'Out for delivery',
    DELIVERED: 'Delivered',
    RETURNED: 'Returned',
    IN_PRODUCTION: 'In production',
}

export function statusText(status: string): string {
    return STATUS_TEXT[status] ?? status
}

/** Newest first, without the "nothing has happened yet" starting rows. */
export function visibleHistory(order: Pick<Order, 'history'>): StatusChange[] {
    return order.history
        .filter((change) => change.toStatus !== 'NOT_REQUIRED' && change.toStatus !== 'NOT_SHIPPED')
        .map((change, index) => ({ change, index }))
        .sort((a, b) => Date.parse(b.change.at) - Date.parse(a.change.at) || b.index - a.index)
        .map(({ change }) => change)
}

export type FulfilmentAction = 'start' | 'pack' | 'ship' | 'deliver'

/** The one step staff can take next, or null when there is none (unpaid, cancelled, completed). */
export function nextFulfilmentAction(order: Pick<Order, 'orderStatus' | 'shipmentStatus'>): FulfilmentAction | null {
    if (order.orderStatus === 'CONFIRMED') return 'start'
    if (order.orderStatus !== 'PROCESSING') return null
    switch (order.shipmentStatus) {
        case 'NOT_SHIPPED':
            return 'pack'
        case 'PACKED':
            return 'ship'
        case 'SHIPPED':
        case 'OUT_FOR_DELIVERY':
            return 'deliver'
        default:
            return null
    }
}

/** Staff may cancel up to and including processing, but not once packed or shipped. */
export function staffCanCancel(order: Pick<Order, 'orderStatus' | 'shipmentStatus'>): boolean {
    return ['PENDING_PAYMENT', 'CONFIRMED', 'PROCESSING'].includes(order.orderStatus) && order.shipmentStatus === 'NOT_SHIPPED'
}

/** A cash-on-delivery order whose cash has not been collected yet. */
export function needsCashCollection(order: Pick<Order, 'paymentMethod' | 'paymentStatus'>): boolean {
    return order.paymentMethod === 'COD' && order.paymentStatus === 'COD_PENDING'
}