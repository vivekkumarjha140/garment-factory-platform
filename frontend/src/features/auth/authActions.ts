import { authApi, type LoginBody } from '../../api/auth'
import { refreshSession } from '../../api/refresh'
import { tokenStore } from '../../api/tokenStore'
import type { AppDispatch } from '../../app/store'
import { sessionCleared, sessionEstablished } from './authSlice'

/** Restores the session after a page load by exchanging the HttpOnly refresh cookie for a new access token. */
export const bootstrapSession = () => async (dispatch: AppDispatch): Promise<void> => {
    try {
        const auth = await refreshSession()
        dispatch(sessionEstablished(auth.user))
    } catch {
        dispatch(sessionCleared())
    }
}

/** Throws an ApiError on failure so the form can show the reason. */
export const signIn = (credentials: LoginBody) => async (dispatch: AppDispatch): Promise<void> => {
    const auth = await authApi.login(credentials)
    tokenStore.set(auth.accessToken)
    dispatch(sessionEstablished(auth.user))
}

export const signOut = () => async (dispatch: AppDispatch): Promise<void> => {
    try {
        await authApi.logout()
    } catch {
        // The server may be unreachable; the person is still signed out locally.
    }
    tokenStore.set(null)
    dispatch(sessionCleared())
}

export const signOutEverywhere = () => async (dispatch: AppDispatch): Promise<void> => {
    await authApi.logoutAll()
    tokenStore.set(null)
    dispatch(sessionCleared())
}