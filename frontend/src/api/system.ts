import { apiClient } from './client'

export interface PingResponse {
    application: string
    version: string
    profile: string
    serverTime: string
}

export async function fetchPing(): Promise<PingResponse> {
    const { data } = await apiClient.get<PingResponse>('/system/ping')
    return data
}