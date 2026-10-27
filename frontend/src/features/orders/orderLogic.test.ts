import { describe, expect, it } from 'vitest'
import { orderOf, paidOrder } from '../../test/orderFixtures'

import {
    awaitingPayment,
    canCancel,
    canPay,
    invoiceAvailable,
    needsCashCollection,
    nextFulfilmentAction,
    orderBadge,
    placementOutcome,
    staffCanCancel,
    trackingSteps,
    visibleHistory,
} from './orderLogic'

describe('orderBadge', () => {
    it('says awaiting payment, or payment failed', () => {
        expect(orderBadge(orderOf()).label).toBe('Awaiting payment')
        expect(orderBadge(orderOf({ paymentStatus: 'FAILED' })).label).toBe('Payment failed')
    })

    it('tells confirmed online orders from cash-on-delivery ones', () => {
        expect(orderBadge(paidOrder()).label).toBe('Confirmed')
        expect(orderBadge(orderOf({ orderStatus: 'CONFIRMED', paymentStatus: 'COD_PENDING', paymentMethod: 'COD' })).label).toBe('Confirmed · pay on delivery')
    })

    it('lets shipping progress take over once the order has left', () => {
        expect(orderBadge(paidOrder({ shipmentStatus: 'SHIPPED' })).label).toBe('Shipped')
        expect(orderBadge(paidOrder({ shipmentStatus: 'DELIVERED' })).label).toBe('Delivered')
    })

    it('explains what happened to the money of a cancelled order', () => {
        expect(orderBadge(orderOf({ orderStatus: 'CANCELLED' })).label).toBe('Cancelled')
        expect(orderBadge(orderOf({ orderStatus: 'CANCELLED', paymentStatus: 'PAID' })).label).toBe('Cancelled · refund on its way')
        expect(orderBadge(orderOf({ orderStatus: 'CANCELLED', paymentStatus: 'REFUNDED' })).label).toBe('Cancelled · refunded')
    })
})

describe('what a customer may do', () => {
    it('can pay an unpaid online order that has not expired, also after a failed attempt', () => {
        expect(canPay(orderOf())).toBe(true)
        expect(canPay(orderOf({ paymentStatus: 'FAILED' }))).toBe(true)
    })

    it('cannot pay once paid, expired, cancelled, or when paying on delivery', () => {
        expect(canPay(paidOrder())).toBe(false)
        expect(canPay(orderOf({ paymentDueAt: '2020-01-01T00:00:00Z' }))).toBe(false)
        expect(canPay(orderOf({ orderStatus: 'CANCELLED' }))).toBe(false)
        expect(canPay(orderOf({ paymentMethod: 'COD', orderStatus: 'CONFIRMED', paymentStatus: 'COD_PENDING' }))).toBe(false)
    })

    it('can cancel only before shipping starts', () => {
        expect(canCancel(orderOf())).toBe(true)
        expect(canCancel(paidOrder())).toBe(true)
        expect(canCancel(paidOrder({ shipmentStatus: 'SHIPPED' }))).toBe(false)
        expect(canCancel(paidOrder({ orderStatus: 'PROCESSING' }))).toBe(false)
        expect(canCancel(orderOf({ orderStatus: 'CANCELLED' }))).toBe(false)
    })

    it('has an invoice only for a paid, confirmed order', () => {
        expect(invoiceAvailable(paidOrder())).toBe(true)
        expect(invoiceAvailable(orderOf())).toBe(false)
        expect(invoiceAvailable(orderOf({ orderStatus: 'CANCELLED', paymentStatus: 'PAID' }))).toBe(false)
    })
})

describe('placementOutcome', () => {
    it('trusts only the server status', () => {
        expect(placementOutcome(orderOf())).toBe('awaiting')
        expect(placementOutcome(paidOrder())).toBe('confirmed')
        expect(placementOutcome(orderOf({ paymentStatus: 'FAILED' }))).toBe('failed')
        expect(placementOutcome(orderOf({ orderStatus: 'CANCELLED' }))).toBe('cancelled')
        expect(placementOutcome(orderOf({ paymentMethod: 'COD', orderStatus: 'CONFIRMED', paymentStatus: 'COD_PENDING' }))).toBe('cod-confirmed')
    })

    it('knows when it is still waiting for a payment', () => {
        expect(awaitingPayment(orderOf())).toBe(true)
        expect(awaitingPayment(paidOrder())).toBe(false)
    })
})

describe('trackingSteps', () => {
    it('marks progress and the next step', () => {
        const states = (order: Parameters<typeof trackingSteps>[0]) => trackingSteps(order).map((s) => s.state)
        expect(states(orderOf())).toEqual(['done', 'current', 'upcoming', 'upcoming'])
        expect(states(paidOrder())).toEqual(['done', 'done', 'current', 'upcoming'])
        expect(states(paidOrder({ shipmentStatus: 'SHIPPED' }))).toEqual(['done', 'done', 'done', 'current'])
        expect(states(paidOrder({ shipmentStatus: 'DELIVERED' }))).toEqual(['done', 'done', 'done', 'done'])
    })
})

describe('visibleHistory', () => {
    it('hides the empty starting rows; the newest change comes first and changes recorded together are listed in reverse order', () => {
        const order = paidOrder({
            history: [
                ...orderOf().history,
                { dimension: 'PAYMENT', fromStatus: 'PENDING', toStatus: 'PAID', reason: 'Payment captured', at: '2026-10-26T10:05:00Z' },
                { dimension: 'ORDER', fromStatus: 'PENDING_PAYMENT', toStatus: 'CONFIRMED', reason: 'Payment confirmed', at: '2026-10-26T10:05:00Z' },
            ],
        })

        expect(visibleHistory(order).map((change) => change.toStatus)).toEqual([
            'CONFIRMED',
            'PAID',
            'PENDING',
            'PENDING_PAYMENT',
        ])
    })
})


describe('staff fulfilment', () => {
    it('offers exactly the next step', () => {
        expect(nextFulfilmentAction(orderOf())).toBeNull()
        expect(nextFulfilmentAction(paidOrder())).toBe('start')
        expect(nextFulfilmentAction(paidOrder({ orderStatus: 'PROCESSING' }))).toBe('pack')
        expect(nextFulfilmentAction(paidOrder({ orderStatus: 'PROCESSING', shipmentStatus: 'PACKED' }))).toBe('ship')
        expect(nextFulfilmentAction(paidOrder({ orderStatus: 'PROCESSING', shipmentStatus: 'SHIPPED' }))).toBe('deliver')
        expect(nextFulfilmentAction(paidOrder({ orderStatus: 'COMPLETED', shipmentStatus: 'DELIVERED' }))).toBeNull()
        expect(nextFulfilmentAction(orderOf({ orderStatus: 'CANCELLED' }))).toBeNull()
    })

    it('lets staff cancel up to processing but not once packed', () => {
        expect(staffCanCancel(paidOrder({ orderStatus: 'PROCESSING' }))).toBe(true)
        expect(staffCanCancel(paidOrder({ orderStatus: 'PROCESSING', shipmentStatus: 'PACKED' }))).toBe(false)
        expect(staffCanCancel(orderOf({ orderStatus: 'COMPLETED' }))).toBe(false)
    })

    it('knows when cash still has to be collected', () => {
        expect(needsCashCollection(orderOf({ paymentMethod: 'COD', paymentStatus: 'COD_PENDING' }))).toBe(true)
        expect(needsCashCollection(paidOrder())).toBe(false)
    })
})
