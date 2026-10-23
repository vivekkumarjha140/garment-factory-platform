import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { UserSummary } from '../../types/auth'

/** unknown = the session has not been restored yet (page just loaded). */
export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous'

export interface AuthState {
    status: AuthStatus
    user: UserSummary | null
}

const initialState: AuthState = { status: 'unknown', user: null }

const authSlice = createSlice({
    name: 'auth',
    initialState,
    reducers: {
        sessionEstablished(state, action: PayloadAction<UserSummary>) {
            state.status = 'authenticated'
            state.user = action.payload
        },
        sessionCleared(state) {
            state.status = 'anonymous'
            state.user = null
        },
        /** Keeps the name in the header in step with a saved profile change. */
        profileNameChanged(state, action: PayloadAction<string>) {
            if (state.user) state.user.fullName = action.payload
        },
    },
})

export const { sessionEstablished, sessionCleared, profileNameChanged } = authSlice.actions
export const authReducer = authSlice.reducer