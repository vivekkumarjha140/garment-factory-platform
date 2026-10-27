import type { InitiatePaymentResponse } from '../../types/order'

const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js'

declare global {
    interface Window {
        Razorpay?: new (options: Record<string, unknown>) => {
            open: () => void
            on: (event: string, handler: (response: unknown) => void) => void
        }
    }
}

let pending: Promise<void> | null = null

/** Loads Razorpay's checkout script once. A failed load can be retried by calling this again. */
export function loadRazorpay(): Promise<void> {
    if (window.Razorpay) return Promise.resolve()
    if (pending) return pending
    pending = new Promise<void>((resolve, reject) => {
        const script = document.createElement('script')
        script.src = SCRIPT_URL
        script.async = true
        script.onload = () => {
            pending = null
            if (window.Razorpay) resolve()
            else reject(new Error('The payment window could not be started. Please try again.'))
        }
        script.onerror = () => {
            pending = null
            script.remove()
            reject(new Error('Could not load the payment window. Check your connection and try again.'))
        }
        document.head.appendChild(script)
    })
    return pending
}

export interface RazorpayCallbacks {
    /** The customer finished paying in the widget. This is only a hint: the server verifies it with Razorpay. */
    onPaymentId: (paymentId: string) => void
    onDismiss: () => void
    onFailure: (message: string) => void
}

/** Opens the Razorpay window. The signature the widget returns is deliberately ignored: the browser decides nothing. */
export function openRazorpay(checkout: InitiatePaymentResponse, callbacks: RazorpayCallbacks): void {
    if (!window.Razorpay) throw new Error('The payment window is not loaded.')
    const instance = new window.Razorpay({
        key: checkout.keyId,
        amount: checkout.amountPaise,
        currency: checkout.currency,
        order_id: checkout.razorpayOrderId,
        name: 'Garment Factory',
        description: `Order ${checkout.orderNumber}`,
        prefill: {
            name: checkout.customerName,
            email: checkout.customerEmail,
            contact: checkout.customerPhone ?? undefined,
        },
        theme: { color: '#4f46e5' },
        handler: (response: unknown) => {
            const paymentId = (response as { razorpay_payment_id?: unknown } | null)?.razorpay_payment_id
            if (typeof paymentId === 'string' && paymentId !== '') {
                callbacks.onPaymentId(paymentId)
            } else {
                callbacks.onFailure('The payment could not be confirmed. Please check your order page before paying again.')
                callbacks.onDismiss()
            }
        },
        modal: { ondismiss: callbacks.onDismiss },
    })
    instance.on('payment.failed', (response: unknown) => {
        const description = (response as { error?: { description?: unknown } } | null)?.error?.description
        callbacks.onFailure(typeof description === 'string' && description !== '' ? description : 'The payment failed.')
    })
    instance.open()
}