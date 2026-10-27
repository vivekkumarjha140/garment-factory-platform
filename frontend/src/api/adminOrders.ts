import type { PageResponse } from '../types/auth'
import type { AdminOrder, AdminOrderSummary, OrderStatus, PaymentMethod, PaymentStatus, ShipmentStatus } from '../types/order'
import { call } from './call'
import { apiClient } from './client'
import { downloadPdf, type InvoiceDownload } from './orders'

const BASE = '/admin/orders'
const id = encodeURIComponent

export interface AdminOrderFilters {
    q?: string
    orderStatus?: OrderStatus
    paymentStatus?: PaymentStatus
    shipmentStatus?: ShipmentStatus
    paymentMethod?: PaymentMethod
    page?: number
    size?: number
}

export const adminOrdersApi = {
    list: (filters: AdminOrderFilters) => call(apiClient.get<PageResponse<AdminOrderSummary>>(BASE, { params: filters })),
    get: (orderNumber: string) => call(apiClient.get<AdminOrder>(`${BASE}/${id(orderNumber)}`)),
    startProcessing: (orderNumber: string, note?: string) =>
        call(apiClient.post<AdminOrder>(`${BASE}/${id(orderNumber)}/start-processing`, { note: note ?? null })),
    pack: (orderNumber: string, note?: string) => call(apiClient.post<AdminOrder>(`${BASE}/${id(orderNumber)}/pack`, { note: note ?? null })),
    ship: (orderNumber: string, note?: string) => call(apiClient.post<AdminOrder>(`${BASE}/${id(orderNumber)}/ship`, { note: note ?? null })),
    deliver: (orderNumber: string, cashCollected: boolean, note?: string) =>
        call(apiClient.post<AdminOrder>(`${BASE}/${id(orderNumber)}/deliver`, { cashCollected, note: note ?? null })),
    cancel: (orderNumber: string, reason: string) => call(apiClient.post<AdminOrder>(`${BASE}/${id(orderNumber)}/cancel`, { reason })),
    downloadInvoice: (orderNumber: string): Promise<InvoiceDownload> =>
        downloadPdf(`${BASE}/${id(orderNumber)}/invoice`, `invoice-${orderNumber}.pdf`),
}