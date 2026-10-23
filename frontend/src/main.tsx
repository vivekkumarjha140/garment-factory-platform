import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router/dom'
import './index.css'
import { initAuth } from './app/initAuth'
import { store } from './app/store'
import { router } from './app/router'

const queryClient = new QueryClient({
    defaultOptions: {
        queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 },
    },
})

// Runs once, outside React, so StrictMode's double effects cannot start two session restores.
initAuth(store)

// The theme must apply on every page (including sign-in), not only where the toggle is shown.
function applyTheme() {
    document.documentElement.classList.toggle('dark', store.getState().ui.theme === 'dark')
}
applyTheme()
store.subscribe(applyTheme)

// Cached profile and address data belongs to the person who was signed in: drop it on sign-out
// or session expiry, so the next person on this browser can never see it.
let previousStatus = store.getState().auth.status
store.subscribe(() => {
    const status = store.getState().auth.status
    if (status !== previousStatus) {
        previousStatus = status
        if (status === 'anonymous') queryClient.clear()
    }
})

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <Provider store={store}>
            <QueryClientProvider client={queryClient}>
                <RouterProvider router={router} />
            </QueryClientProvider>
        </Provider>
    </StrictMode>,
)