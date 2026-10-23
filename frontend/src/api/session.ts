import type { AuthResponse } from '../types/auth'

/**
 * Tiny event hub between the HTTP layer and the Redux store. The HTTP layer must not import the
 * store (that would be a circular dependency), so it announces what happened and the app reacts.
 */
const expiredListeners = new Set<() => void>()
const refreshedListeners = new Set<(auth: AuthResponse) => void>()

export function onSessionExpired(listener: () => void): () => void {
    expiredListeners.add(listener)
    return () => {
        expiredListeners.delete(listener)
    }
}

export function onSessionRefreshed(listener: (auth: AuthResponse) => void): () => void {
    refreshedListeners.add(listener)
    return () => {
        refreshedListeners.delete(listener)
    }
}

export function emitSessionExpired(): void {
    expiredListeners.forEach((listener) => listener())
}

export function emitSessionRefreshed(auth: AuthResponse): void {
    refreshedListeners.forEach((listener) => listener(auth))
}