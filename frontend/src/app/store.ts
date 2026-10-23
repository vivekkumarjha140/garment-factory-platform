import { combineReducers, configureStore } from '@reduxjs/toolkit'
import { authReducer } from '../features/auth/authSlice'
import { uiReducer } from '../features/ui/uiSlice'

const rootReducer = combineReducers({ ui: uiReducer, auth: authReducer })

export type RootState = ReturnType<typeof rootReducer>

/** A factory so tests can build a store with a chosen starting state. */
export function createStore(preloadedState?: Partial<RootState>) {
    return configureStore({ reducer: rootReducer, preloadedState })
}

export type AppStore = ReturnType<typeof createStore>
export type AppDispatch = AppStore['dispatch']

export const store = createStore()