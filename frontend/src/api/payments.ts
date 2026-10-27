import type { InitiatePaymentResponse, SyncPaymentResponse } from '../types/order'
import { call } from './call'
import { apiClient } from './client'

export const paymentsApi = {
    /** Starts (or resumes) online payment. Repeating it returns the same Razorpay order. */
    initiate: (orderNumber: string) =>
        call(apiClient.post<InitiatePaymentResponse>(`/payments/orders/${encodeURIComponent(orderNumber)}/initiate`)),
    /** The browser only names the payment; the server asks Razorpay what really happened. */
    sync: (orderNumber: string, razorpayPaymentId: string) =>
        call(apiClient.post<SyncPaymentResponse>(`/payments/orders/${encodeURIComponent(orderNumber)}/sync`, { razorpayPaymentId })),
}