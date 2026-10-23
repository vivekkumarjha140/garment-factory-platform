import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../../../api/auth'
import { ApiError } from '../../../api/errors'
import { tokenStore } from '../../../api/tokenStore'
import { renderWithProviders } from '../../../test/utils'
import { LoginPage } from './LoginPage'

vi.mock('../../../api/auth', () => ({
    authApi: { login: vi.fn(), resendVerification: vi.fn() },
}))

const login = vi.mocked(authApi.login)
const resendVerification = vi.mocked(authApi.resendVerification)

function page() {
    return renderWithProviders(
        <Routes>
            <Route path="/login" element={<LoginPage />} />
    <Route path="/" element={<div>home page</div>} />
    </Routes>,
    { route: '/login' },
)
}

    async function fillAndSubmit(email: string, password: string) {
        const user = userEvent.setup()
        await user.type(screen.getByLabelText('Email'), email)
        await user.type(screen.getByLabelText('Password'), password)
        await user.click(screen.getByRole('button', { name: 'Sign in' }))
    }

    beforeEach(() => {
        vi.clearAllMocks()
        tokenStore.set(null)
    })

    describe('LoginPage', () => {
        it('validates the fields before calling the API', async () => {
            page()

            await userEvent.setup().click(screen.getByRole('button', { name: 'Sign in' }))

            expect(await screen.findByText('Enter your email address')).toBeInTheDocument()
            expect(screen.getByText('Enter your password')).toBeInTheDocument()
            expect(login).not.toHaveBeenCalled()
        })

        it('signs in, keeps the token in memory and moves on to the home page', async () => {
            login.mockResolvedValue({
                accessToken: 'access-1',
                tokenType: 'Bearer',
                expiresIn: 900,
                user: { publicId: 'p1', email: 'alice@example.com', fullName: 'A B', roles: ['CUSTOMER'], permissions: [] },
            })
            const { store } = page()

            await fillAndSubmit('alice@example.com', 'Str0ngPassw0rd')

            expect(await screen.findByText('home page')).toBeInTheDocument()
            expect(login).toHaveBeenCalledWith({ email: 'alice@example.com', password: 'Str0ngPassw0rd' })
            expect(tokenStore.get()).toBe('access-1')
            expect(store.getState().auth.status).toBe('authenticated')
        })

        it('explains an unverified account and offers to resend the email', async () => {
            login.mockRejectedValue(new ApiError(403, 'EMAIL_NOT_VERIFIED', 'Please verify your email address before signing in.'))
            resendVerification.mockResolvedValue({ message: 'ok' })
            page()

            await fillAndSubmit('alice@example.com', 'Str0ngPassw0rd')

            expect(await screen.findByRole('alert')).toHaveTextContent('Please verify your email address')
            await userEvent.setup().click(screen.getByRole('button', { name: 'Resend verification email' }))
            expect(resendVerification).toHaveBeenCalledWith('alice@example.com')
        })

        it('tells the person how long to wait when rate limited', async () => {
            login.mockRejectedValue(new ApiError(429, 'TOO_MANY_REQUESTS', 'Too many requests.', {}, 42))
            page()

            await fillAndSubmit('alice@example.com', 'Wr0ngPassword99')

            expect(await screen.findByRole('alert')).toHaveTextContent('try again in 42 seconds')
        })
    })