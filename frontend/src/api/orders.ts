import { isAxiosError } from 'axios'
import type { Order, OrderSummary, PlaceOrderBody } from '../types/order'
import type { PageResponse } from '../types/auth'
import { call } from './call'
import { apiClient } from './client'
import { toApiError } from './errors'

export interface InvoiceDownload {
    blob: Blob
    fileName: string
}

function readBlobText(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result ?? ''))
        reader.onerror = () => reject(reader.error)
        reader.readAsText(blob)
    })
}

/** With responseType 'blob' an error body arrives as a Blob; turn it back into the problem JSON so the message survives. */
async function withReadableProblem(error: unknown): Promise<unknown> {
    if (isAxiosError(error) && error.response?.data instanceof Blob) {
        try {
            error.response.data = JSON.parse(await readBlobText(error.response.data))
        } catch {
            // keep the generic error
        }
    }
    return error
}

function fileNameFrom(disposition: unknown): string | null {
    const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(String(disposition ?? ''))
    return match ? decodeURIComponent(match[1]) : null
}

/** Downloads a PDF with the signed-in token. An error body that arrives as a Blob is turned back into a readable ApiError. */
export async function downloadPdf(path: string, fallbackName: string): Promise<InvoiceDownload> {
    try {
        const response = await apiClient.get<Blob>(path, { responseType: 'blob' })
        return { blob: response.data, fileName: fileNameFrom(response.headers['content-disposition']) ?? fallbackName }
    } catch (error) {
        throw toApiError(await withReadableProblem(error))
    }
}

export const ordersApi = {
    /** The key makes the call safe to repeat: the same key never creates a second order. */
    place: (body: PlaceOrderBody, idempotencyKey: string) =>
        call(apiClient.post<Order>('/orders', body, { headers: { 'Idempotency-Key': idempotencyKey } })),
    list: (page: number, size: number) =>
        call(apiClient.get<PageResponse<OrderSummary>>('/orders', { params: { page, size } })),
    get: (orderNumber: string) => call(apiClient.get<Order>(`/orders/${encodeURIComponent(orderNumber)}`)),
    cancel: (orderNumber: string, reason?: string) =>
        call(apiClient.post<Order>(`/orders/${encodeURIComponent(orderNumber)}/cancel`, { reason: reason ?? null })),
    downloadInvoice: (orderNumber: string): Promise<InvoiceDownload> =>
        downloadPdf(
            `/orders/${encodeURIComponent(orderNumber)}/invoice`,
            `invoice-${orderNumber}.pdf`,
        ),
}