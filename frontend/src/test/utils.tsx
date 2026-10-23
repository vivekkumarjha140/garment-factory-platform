import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router'
import { createStore, type RootState } from '../app/store'

interface Options {
    state?: Partial<RootState>
    route?: string
}

/** Renders with a fresh store, query client and in-memory router. */
export function renderWithProviders(ui: ReactElement, { state, route = '/' }: Options = {}) {
    const store = createStore(state)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const result = render(
        <Provider store={store}>
            <QueryClientProvider client={queryClient}>
                <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
            </QueryClientProvider>
        </Provider>,
    )
    return { store, ...result }
}