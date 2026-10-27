import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { InitiatePaymentResponse } from '../../types/order'
import { loadRazorpay, openRazorpay } from './razorpay'

const SRC = 'https://checkout.razorpay.com/v1/checkout.js'
const scripts = () => document.head.querySelectorAll(`script[src="${SRC}"]`)

type Options = Record<string, unknown> & { handler: (response: unknown) => void; modal: { ondismiss: () => void } }

const checkout: InitiatePaymentResponse = {
    keyId: 'rzp_test_key',
    razorpayOrderId: 'order_abc',
    amountPaise: 110000,
    currency: 'INR',
    orderNumber: 'GF-2026-000001',
    customerName: 'Test User',
    customerEmail: 'test@example.com',
    customerPhone: null,
}

beforeEach(() => {
    delete window.Razorpay
    scripts().forEach((script) => script.remove())
})

describe('loadRazorpay', () => {
    it('adds the script once and resolves when it has loaded', async () => {
        const first = loadRazorpay()
        const second = loadRazorpay()
        expect(scripts()).toHaveLength(1)

        window.Razorpay = vi.fn() as never
        scripts()[0].dispatchEvent(new Event('load'))

        await expect(first).resolves.toBeUndefined()
        await expect(second).resolves.toBeUndefined()
    })

    it('resolves at once when Razorpay is already there', async () => {
        window.Razorpay = vi.fn() as never
        await expect(loadRazorpay()).resolves.toBeUndefined()
        expect(scripts()).toHaveLength(0)
    })

    it('rejects when the script is blocked and can be retried', async () => {
        const failed = loadRazorpay()
        scripts()[0].dispatchEvent(new Event('error'))
        await expect(failed).rejects.toThrow(/Could not load the payment window/)

        const retry = loadRazorpay()
        expect(scripts()).toHaveLength(1)
        window.Razorpay = vi.fn() as never
        scripts()[0].dispatchEvent(new Event('load'))
        await expect(retry).resolves.toBeUndefined()
    })
})

describe('openRazorpay', () => {
    function install() {
        const instance = { open: vi.fn(), on: vi.fn() }
        const constructor = vi.fn(function (_options: Options) {
            return instance
        })
        window.Razorpay = constructor as never
        return { instance, constructor }
    }

    it('opens the widget for the server-created order and nothing else', () => {
        const { instance, constructor } = install()
        openRazorpay(checkout, { onPaymentId: vi.fn(), onDismiss: vi.fn(), onFailure: vi.fn() })

        expect(constructor.mock.calls[0][0]).toMatchObject({
            key: 'rzp_test_key',
            order_id: 'order_abc',
            amount: 110000,
            currency: 'INR',
        })
        expect(instance.open).toHaveBeenCalledOnce()
    })

    it('passes only the payment id on, never the signature', () => {
        const { constructor } = install()
        const onPaymentId = vi.fn()
        openRazorpay(checkout, { onPaymentId, onDismiss: vi.fn(), onFailure: vi.fn() })

        constructor.mock.calls[0][0].handler({
            razorpay_payment_id: 'pay_1',
            razorpay_order_id: 'order_abc',
            razorpay_signature: 'sig',
        })

        expect(onPaymentId).toHaveBeenCalledWith('pay_1')
    })

    it('reports a dismissed window and a failed payment', () => {
        const { instance, constructor } = install()
        const onDismiss = vi.fn()
        const onFailure = vi.fn()
        openRazorpay(checkout, { onPaymentId: vi.fn(), onDismiss, onFailure })

        constructor.mock.calls[0][0].modal.ondismiss()
        const [event, handler] = instance.on.mock.calls[0] as [string, (response: unknown) => void]
        handler({ error: { description: 'Card declined' } })

        expect(onDismiss).toHaveBeenCalledOnce()
        expect(event).toBe('payment.failed')
        expect(onFailure).toHaveBeenCalledWith('Card declined')
    })

    it('does not treat a success without a payment id as paid', () => {
        const { constructor } = install()
        const onPaymentId = vi.fn()
        const onDismiss = vi.fn()
        const onFailure = vi.fn()
        openRazorpay(checkout, { onPaymentId, onDismiss, onFailure })

        constructor.mock.calls[0][0].handler({})

        expect(onPaymentId).not.toHaveBeenCalled()
        expect(onFailure).toHaveBeenCalled()
        expect(onDismiss).toHaveBeenCalled()
    })
})