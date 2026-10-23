import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/utils'
import type { UserSummary } from '../../types/auth'
import { RequireAuth, RequireGuest, RequirePermission } from './guards'

const user: UserSummary = {
    publicId: 'p1',
    email: 'staff@example.com',
    fullName: 'Staff Member',
    roles: ['FACTORY_MANAGER'],
    permissions: ['USER_VIEW'],
}
const authenticated = { auth: { status: 'authenticated' as const, user } }
const anonymous = { auth: { status: 'anonymous' as const, user: null } }
const restoring = { auth: { status: 'unknown' as const, user: null } }

function routes() {
    return (
        <Routes>
            <Route path="/" element={<div>home page</div>} />
    <Route path="/login" element={<div>login page</div>} />
    <Route element={<RequireGuest />}>
    <Route path="/guest" element={<div>guest page</div>} />
    </Route>
    <Route element={<RequireAuth />}>
    <Route path="/secret" element={<div>secret page</div>} />
    <Route element={<RequirePermission permission="USER_VIEW" />}>
    <Route path="/users" element={<div>users page</div>} />
    </Route>
    <Route element={<RequirePermission permission="ROLE_MANAGE" />}>
    <Route path="/roles" element={<div>roles page</div>} />
    </Route>
    </Route>
    </Routes>
)
}

    describe('route guards', () => {
        it('sends anonymous visitors to the login page', () => {
            renderWithProviders(routes(), { state: anonymous, route: '/secret' })

            expect(screen.getByText('login page')).toBeInTheDocument()
            expect(screen.queryByText('secret page')).not.toBeInTheDocument()
        })

        it('shows only a spinner while the session is being restored', () => {
            renderWithProviders(routes(), { state: restoring, route: '/secret' })

            expect(screen.getByRole('status')).toBeInTheDocument()
            expect(screen.queryByText('secret page')).not.toBeInTheDocument()
            expect(screen.queryByText('login page')).not.toBeInTheDocument()
        })

        it('lets signed-in users through', () => {
            renderWithProviders(routes(), { state: authenticated, route: '/secret' })

            expect(screen.getByText('secret page')).toBeInTheDocument()
        })

        it('allows a page when the permission is held', () => {
            renderWithProviders(routes(), { state: authenticated, route: '/users' })

            expect(screen.getByText('users page')).toBeInTheDocument()
        })

        it('shows access denied when the permission is missing', () => {
            renderWithProviders(routes(), { state: authenticated, route: '/roles' })

            expect(screen.getByRole('heading', { name: 'Access denied' })).toBeInTheDocument()
            expect(screen.queryByText('roles page')).not.toBeInTheDocument()
        })

        it('sends signed-in users away from the guest pages', () => {
            renderWithProviders(routes(), { state: authenticated, route: '/guest' })

            expect(screen.getByText('home page')).toBeInTheDocument()
        })
    })