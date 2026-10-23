import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthResponse } from '../types/auth'
import { apiClient } from './client'
import { rawClient } from './refresh'
import { onSessionExpired } from './session'
import { tokenStore } from './tokenStore'

const FRESH: AuthResponse = {
    accessToken: 'fresh-token',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: { publicId: 'p1', email: 'a@b.c', fullName: 'A B', roles: ['CUSTOMER'], permissions: [] },
}

function reply(config: InternalAxiosRequestConfig, status: number, data: unknown): Promise<AxiosResponse> {
    const response: AxiosResponse = { data, status, statusText: '', headers: {}, config }
    return status < 400
        ? Promise.resolve(response)
        : Promise.reject(new AxiosError('Request failed', String(status), config, undefined, response))
}

let refreshCalls = 0
let seenAuthorization: unknown[] = []

beforeEach(() => {
    refreshCalls = 0
    seenAuthorization = []
    tokenStore.set('stale-token')
    // The "server": only the fresh token is accepted, and refreshing takes a moment.
    rawClient.defaults.adapter = async (config) => {
        refreshCalls += 1
        await new Promise((resolve) => setTimeout(resolve, 10))
        return reply(config, 200, FRESH)
    }
    apiClient.defaults.adapter = (config) => {
        const authorization = config.headers.get('Authorization')
        seenAuthorization.push(authorization)
        return reply(config, authorization === 'Bearer fresh-token' ? 200 : 401, { ok: true })
    }
})

afterEach(() => {
    tokenStore.set(null)
})

describe('apiClient', () => {
    it('shares one refresh between simultaneous 401s and retries every request with the new token', async () => {
        const responses = await Promise.all([apiClient.get('/a'), apiClient.get('/b'), apiClient.get('/c')])

        expect(responses.map((response) => response.status)).toEqual([200, 200, 200])
        expect(refreshCalls).toBe(1)
        expect(tokenStore.get()).toBe('fresh-token')
    })

    it('does not try to refresh when login itself answers 401', async () => {
        apiClient.defaults.adapter = (config) => reply(config, 401, { code: 'INVALID_CREDENTIALS' })

        await expect(apiClient.post('/auth/login', {})).rejects.toBeInstanceOf(AxiosError)

        expect(refreshCalls).toBe(0)
    })

    it('announces an expired session and gives up when the refresh fails', async () => {
        rawClient.defaults.adapter = (config) => reply(config, 401, { code: 'INVALID_REFRESH_TOKEN' })
        const expired = vi.fn()
        const unsubscribe = onSessionExpired(expired)

        await expect(apiClient.get('/orders')).rejects.toBeInstanceOf(AxiosError)

        unsubscribe()
        expect(expired).toHaveBeenCalledTimes(1)
        expect(tokenStore.get()).toBeNull()
    })

    it('retries a request only once', async () => {
        apiClient.defaults.adapter = (config) => {
            seenAuthorization.push(config.headers.get('Authorization'))
            return reply(config, 401, {})
        }

        await expect(apiClient.get('/always-401')).rejects.toBeInstanceOf(AxiosError)

        expect(seenAuthorization).toEqual(['Bearer stale-token', 'Bearer fresh-token'])
        expect(refreshCalls).toBe(1)
    })
})