import type { AuthResponse } from '../types/auth'
import { createHttp } from './http'
import { emitSessionRefreshed } from './session'
import { tokenStore } from './tokenStore'

/** A client without interceptors, so refreshing can never trigger another refresh. */
export const rawClient = createHttp()

const LOCK_NAME = 'gf-session-refresh'
let inFlight: Promise<AuthResponse> | null = null

/**
 * Serialises refreshes across browser tabs (Web Locks API). The server treats a reused refresh
 * token as theft and ends the whole session, so two tabs must never refresh at the same moment.
 * Where the API is missing the refresh simply runs without the lock.
 */

function withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined' ? undefined : navigator.locks

  if (!locks) {
    return task()
  }

  return new Promise<T>((resolve, reject) => {
    locks.request(LOCK_NAME, async () => {
      try {
        resolve(await task())
      } catch (error) {
        reject(error)
        throw error
      }
    }).catch(reject)
  })
}

/** Single flight: any number of callers at the same time share one refresh request. */
export function refreshSession(): Promise<AuthResponse> {
    if (!inFlight) {
        inFlight = withCrossTabLock(async () => {
            const { data } = await rawClient.post<AuthResponse>('/auth/refresh')
            return data
        })
            .then((auth) => {
                tokenStore.set(auth.accessToken)
                emitSessionRefreshed(auth)
                return auth
            })
            .catch((error: unknown) => {
                tokenStore.set(null)
                throw error
            })
            .finally(() => {
                inFlight = null
            })
    }
    return inFlight
}