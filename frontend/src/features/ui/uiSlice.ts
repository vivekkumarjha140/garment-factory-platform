import { createSlice } from '@reduxjs/toolkit'

export type ThemeMode = 'light' | 'dark'

interface UiState {
    theme: ThemeMode
}

function initialTheme(): ThemeMode {
    try {
        const saved = localStorage.getItem('gf.theme')
        if (saved === 'light' || saved === 'dark') return saved
    } catch {
        // Storage can be unavailable (private mode); fall back to the OS preference.
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

const uiSlice = createSlice({
    name: 'ui',
    initialState: { theme: initialTheme() } as UiState,
    reducers: {
        toggleTheme(state) {
            state.theme = state.theme === 'dark' ? 'light' : 'dark'
        },
    },
})

export const { toggleTheme } = uiSlice.actions
export const uiReducer = uiSlice.reducer