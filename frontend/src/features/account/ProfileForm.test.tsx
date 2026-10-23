import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { customerApi } from '../../api/customer'
import { ApiError } from '../../api/errors'
import { renderWithProviders } from '../../test/utils'
import type { CustomerProfile } from '../../types/customer'
import { ProfileForm } from './ProfileForm'

vi.mock('../../api/customer', () => ({
    customerApi: {
        profile: vi.fn(),
        updateProfile: vi.fn(),
        addresses: vi.fn(),
        createAddress: vi.fn(),
        updateAddress: vi.fn(),
        deleteAddress: vi.fn(),
        makeDefault: vi.fn(),
        states: vi.fn(),
    },
}))

const profile = vi.mocked(customerApi.profile)
const updateProfile = vi.mocked(customerApi.updateProfile)

const PROFILE: CustomerProfile = {
    fullName: 'Asha Verma',
    email: 'asha@example.com',
    phone: null,
    customerType: 'RETAIL',
    marketingOptIn: false,
}

function renderForm() {
    return renderWithProviders(<ProfileForm />, {
        state: {
            auth: {
                status: 'authenticated',
                user: { publicId: 'u1', email: 'asha@example.com', fullName: 'Asha Verma', roles: ['CUSTOMER'], permissions: [] },
            },
        },
    })
}

beforeEach(() => {
    vi.clearAllMocks()
    profile.mockResolvedValue(PROFILE)
})

describe('ProfileForm', () => {
    it('shows the saved details, with the email read-only', async () => {
        renderForm()

        expect(await screen.findByDisplayValue('Asha Verma')).toBeInTheDocument()
        expect(screen.getByLabelText('Email')).toHaveValue('asha@example.com')
        expect(screen.getByLabelText('Email')).toHaveAttribute('readonly')
    })

    it('saves the changes and updates the name shown in the header', async () => {
        updateProfile.mockResolvedValue({ ...PROFILE, fullName: 'Asha V', phone: '+919876543210', marketingOptIn: true })
        const { store } = renderForm()
        const user = userEvent.setup()

        const name = await screen.findByLabelText('Full name')
        await screen.findByDisplayValue('Asha Verma')
        await user.clear(name)
        await user.type(name, 'Asha V')
        await user.type(screen.getByLabelText('Phone number'), '+919876543210')
        await user.click(screen.getByRole('checkbox', { name: 'Send me offers and updates' }))
        await user.click(screen.getByRole('button', { name: 'Save changes' }))

        await waitFor(() =>
            expect(updateProfile).toHaveBeenCalledWith({ fullName: 'Asha V', phone: '+919876543210', marketingOptIn: true }),
        )
        expect(await screen.findByText('Your details were saved.')).toBeInTheDocument()
        expect(store.getState().auth.user?.fullName).toBe('Asha V')
    })

    it('does not send a malformed phone number', async () => {
        renderForm()
        const user = userEvent.setup()

        await screen.findByDisplayValue('Asha Verma')
        await user.type(screen.getByLabelText('Phone number'), '12-34')
        await user.click(screen.getByRole('button', { name: 'Save changes' }))

        expect(await screen.findByText('Use 7 to 15 digits, optionally starting with +')).toBeInTheDocument()
        expect(updateProfile).not.toHaveBeenCalled()
    })

    it('shows the server message next to the field it is about', async () => {
        updateProfile.mockRejectedValue(new ApiError(400, 'VALIDATION_FAILED', 'Validation failed.', { phone: 'is not accepted' }))
        renderForm()

        await screen.findByDisplayValue('Asha Verma')
        await userEvent.setup().click(screen.getByRole('button', { name: 'Save changes' }))

        expect(await screen.findByText('is not accepted')).toBeInTheDocument()
    })
})