import { beforeEach, describe, expect, it, vi } from 'vitest'
import { paymentsApi } from '../../api/payments'
import { payOnline } from './payOnline'
import { loadRazorpay, openRazorpay } from './razorpay'

vi.mock('../../api/payments', () => ({ paymentsApi: { initiate: vi.fn(), sync: vi.fn() } }))
vi.mock('./razorpay', () => ({ loadRazorpay: vi.fn(), openRazorpay: vi.fn() }))

const initiate = vi.mocked(paymentsApi.initiate)
const sync = vi.mocked(paymentsApi.sync)
const open = vi.mocked(openRazorpay)

type Callbacks = { onPaymentId: (id: string) => void; onDismiss: () => void; onFailure: (message: string) => void }

beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(loadRazorpay).mockResolvedValue()
    initiate.mockResolvedValue({ keyId: 'k', razorpayOrderId: 'order_1', amountPaise: 100, currency: 'INR', orderNumber: 'GF-1', customerName: 'T', customerEmail: 't@e.com', customerPhone: null })
    sync.mockResolvedValue({ orderNumber: 'GF-1', orderStatus: 'CONFIRMED', paymentStatus: 'PAID', providerStatus: 'captured' })
})

async function started() {
    const result = payOnline('GF-1')
    await vi.waitFor(() => expect(open).toHaveBeenCalled())
    return { result, callbacks: open.mock.calls[0][1] as Callbacks }
}

describe('payOnline', () => {
    it('asks the server to verify the payment and reports it submitted', async () => {
        const { result, callbacks } = await started()
        callbacks.onPaymentId('pay_1')

        await expect(result).resolves.toEqual({ kind: 'submitted' })
        expect(sync).toHaveBeenCalledWith('GF-1', 'pay_1')
    })

    it('still reports submitted when the verification call fails (the webhook will settle it)', async () => {
        sync.mockRejectedValue(new Error('network'))
        const { result, callbacks } = await started()
        callbacks.onPaymentId('pay_1')

        await expect(result).resolves.toEqual({ kind: 'submitted' })
    })

    it('reports a dismissed window', async () => {
        const { result, callbacks } = await started()
        callbacks.onDismiss()

        await expect(result).resolves.toEqual({ kind: 'dismissed' })
        expect(sync).not.toHaveBeenCalled()
    })

    it('reports a failed payment only when the window is then closed', async () => {
        const { result, callbacks } = await started()
        callbacks.onFailure('Card declined')
        callbacks.onDismiss()

        await expect(result).resolves.toEqual({ kind: 'failed', message: 'Card declined' })
    })

    it('lets a retry inside the window succeed after an earlier failure', async () => {
        const { result, callbacks } = await started()
        callbacks.onFailure('Card declined')
        callbacks.onPaymentId('pay_2')

        await expect(result).resolves.toEqual({ kind: 'submitted' })
    })

    it('throws when payment cannot be started', async () => {
        initiate.mockRejectedValue(new Error('provider down'))

        await expect(payOnline('GF-1')).rejects.toThrow('provider down')
        expect(open).not.toHaveBeenCalled()
    })
})