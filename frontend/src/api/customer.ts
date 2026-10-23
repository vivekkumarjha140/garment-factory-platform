import type { Address, AddressBody, CustomerProfile, DefaultKind, UpdateProfileBody } from '../types/customer'
import { call, callVoid } from './call'
import { apiClient } from './client'

export const customerApi = {
    profile: () => call(apiClient.get<CustomerProfile>('/customers/me')),
    updateProfile: (body: UpdateProfileBody) => call(apiClient.patch<CustomerProfile>('/customers/me', body)),
    addresses: () => call(apiClient.get<Address[]>('/customers/me/addresses')),
    createAddress: (body: AddressBody) => call(apiClient.post<Address>('/customers/me/addresses', body)),
    updateAddress: (id: string, body: AddressBody) =>
        call(apiClient.put<Address>(`/customers/me/addresses/${encodeURIComponent(id)}`, body)),
    deleteAddress: (id: string) => callVoid(apiClient.delete(`/customers/me/addresses/${encodeURIComponent(id)}`)),
    makeDefault: (id: string, kind: DefaultKind) =>
        call(apiClient.post<Address>(`/customers/me/addresses/${encodeURIComponent(id)}/default-${kind}`)),
    states: () => call(apiClient.get<string[]>('/customers/reference/states')),
}