import { onSessionExpired, onSessionRefreshed } from '../api/session'
import { tokenStore } from '../api/tokenStore'
import { bootstrapSession } from '../features/auth/authActions'
import { sessionCleared, sessionEstablished } from '../features/auth/authSlice'
import type { AppStore } from './store'

/** Connects the HTTP layer to the store and restores the session after a page load. Call once. */
export function initAuth(store: AppStore): void {
    onSessionExpired(() => {
        tokenStore.set(null)
        store.dispatch(sessionCleared())
    })
    onSessionRefreshed((auth) => {
        store.dispatch(sessionEstablished(auth.user))
    })
    void store.dispatch(bootstrapSession())
}