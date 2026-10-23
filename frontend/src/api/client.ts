import { isAxiosError, type InternalAxiosRequestConfig } from 'axios'
import { createHttp } from './http'
import { refreshSession } from './refresh'
import { emitSessionExpired } from './session'
import { tokenStore } from './tokenStore'

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

/** Public endpoints: a 401 here means "wrong credentials or token", never "access token expired". */
const NO_REFRESH_PATHS = new Set([
    '/auth/register',
    '/auth/verify-email',
    '/auth/resend-verification',
    '/auth/login',
    '/auth/refresh',
    '/auth/logout',
    '/auth/forgot-password',
    '/auth/reset-password',
])

export const apiClient = createHttp()

apiClient.interceptors.request.use((config) => {
    const token = tokenStore.get()
    if (token && !config.headers.has('Authorization')) {
        config.headers.set('Authorization', `Bearer ${token}`)
    }
    return config
})

apiClient.interceptors.response.use(undefined, async (error: unknown) => {
    if (!isAxiosError(error) || error.response?.status !== 401 || !error.config) {
        return Promise.reject(error)
    }
    const original = error.config as RetriableConfig
    const path = (original.url ?? '').split('?')[0]
    if (original._retried || NO_REFRESH_PATHS.has(path)) {
        return Promise.reject(error)
    }
    original._retried = true

    // Another request already refreshed while this one was in flight: just retry with the new token.
    const current = tokenStore.get()
    if (current && original.headers.get('Authorization') !== `Bearer ${current}`) {
        original.headers.set('Authorization', `Bearer ${current}`)
        return apiClient.request(original)
    }

    let accessToken: string
    try {
        accessToken = (await refreshSession()).accessToken
    } catch {
        emitSessionExpired()
        return Promise.reject(error)
    }
    original.headers.set('Authorization', `Bearer ${accessToken}`)
    return apiClient.request(original)
})