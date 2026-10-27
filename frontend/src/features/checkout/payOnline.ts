import { paymentsApi } from '../../api/payments'
import { loadRazorpay, openRazorpay } from './razorpay'

/** What happened in the payment window. "submitted" means paid as far as the customer can tell; the server decides. */
export type PaymentNotice = { kind: 'dismissed' } | { kind: 'failed'; message: string }
export type PayOutcome = { kind: 'submitted' } | PaymentNotice

/**
 * Pays for an existing order online. Throws when payment cannot even start (script blocked, provider down).
 * A failed attempt inside the window does not end the flow: the customer may retry there, so a failure is
 * only reported if they then close the window.
 */
export async function payOnline(orderNumber: string): Promise<PayOutcome> {
    await loadRazorpay()
    const checkout = await paymentsApi.initiate(orderNumber)

    return new Promise<PayOutcome>((resolve) => {
        let settled = false
        let lastFailure: string | null = null
        const finish = (outcome: PayOutcome) => {
            if (!settled) {
                settled = true
                resolve(outcome)
            }
        }
        openRazorpay(checkout, {
            onPaymentId: (paymentId) => {
                // Ask the server to check with Razorpay now. If that call fails the success page keeps polling,
                // and the webhook settles the order anyway.
                paymentsApi
                    .sync(orderNumber, paymentId)
                    .catch(() => undefined)
                    .finally(() => finish({ kind: 'submitted' }))
            },
            onFailure: (message) => {
                lastFailure = message
            },
            onDismiss: () => finish(lastFailure ? { kind: 'failed', message: lastFailure } : { kind: 'dismissed' }),
        })
    })
}