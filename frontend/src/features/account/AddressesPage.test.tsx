import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { customerApi } from '../../api/customer'
import { ApiError } from '../../api/errors'
import { renderWithProviders } from '../../test/utils'
import type { Address } from '../../types/customer'
import { AddressesPage } from './AddressesPage'

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

const addresses = vi.mocked(customerApi.addresses)
const createAddress = vi.mocked(customerApi.createAddress)
const deleteAddress = vi.mocked(customerApi.deleteAddress)
const makeDefault = vi.mocked(customerApi.makeDefault)
const states = vi.mocked(customerApi.states)

const HOME: Address = {
    id: 'a1',
    label: 'Home',
    recipientName: 'Asha Verma',
    phone: '+919876543210',
    line1: '12 MI Road',
    line2: null,
    city: 'Jaipur',
    state: 'Rajasthan',
    pincode: '302001',
    defaultShipping: true,
    defaultBilling: true,
}
const OFFICE: Address = {
    ...HOME,
    id: 'a2',
    label: 'Office',
    line1: '5 Nehru Place',
    city: 'New Delhi',
    state: 'Delhi',
    pincode: '110019',
    defaultShipping: false,
    defaultBilling: false,
}

beforeEach(() => {
    vi.clearAllMocks()
    addresses.mockResolvedValue([HOME, OFFICE])
    states.mockResolvedValue(['Delhi', 'Goa', 'Rajasthan'])
})

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
    await screen.findByRole('option', { name: 'Rajasthan' })
    await user.type(screen.getByLabelText('Recipient name'), 'Asha Verma')
    await user.type(screen.getByLabelText('Phone number'), '+919876543210')
    await user.type(screen.getByLabelText('House, flat or street'), '12 MI Road')
    await user.type(screen.getByLabelText('City'), 'Jaipur')
    await user.type(screen.getByLabelText('Pincode'), '302001')
    await user.selectOptions(screen.getByLabelText('State or union territory'), 'Rajasthan')
}

describe('AddressesPage', () => {
    it('lists the addresses, marks the defaults and offers to make another one the default', async () => {
        renderWithProviders(<AddressesPage />)

        expect(await screen.findByRole('heading', { name: 'Home', level: 3 })).toBeInTheDocument()
        expect(screen.getByText('Default shipping')).toBeInTheDocument()
        expect(screen.getByText('Default billing')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Make Office the default shipping address' })).toBeInTheDocument()
        expect(screen.queryByRole('button', { name: 'Make Home the default shipping address' })).not.toBeInTheDocument()
    })

    it('makes an address the default shipping address', async () => {
        makeDefault.mockResolvedValue({ ...OFFICE, defaultShipping: true })
        renderWithProviders(<AddressesPage />)

        await userEvent.setup().click(await screen.findByRole('button', { name: 'Make Office the default shipping address' }))

        await waitFor(() => expect(makeDefault).toHaveBeenCalledWith('a2', 'shipping'))
        expect(await screen.findByText('Default address updated.')).toBeInTheDocument()
    })

    it('deletes an address only after the customer confirms', async () => {
        deleteAddress.mockResolvedValue(undefined)
        renderWithProviders(<AddressesPage />)
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Delete Office' }))
        expect(deleteAddress).not.toHaveBeenCalled()
        await user.click(screen.getByRole('button', { name: 'Yes, delete' }))

        await waitFor(() => expect(deleteAddress).toHaveBeenCalledWith('a2'))
        expect(await screen.findByText('Address deleted.')).toBeInTheDocument()
    })

    it('does not send an incomplete address and shows what is missing', async () => {
        renderWithProviders(<AddressesPage />)
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Add address' }))
        await user.click(screen.getByRole('button', { name: 'Save address' }))

        expect(await screen.findByText('Enter the recipient name')).toBeInTheDocument()
        expect(screen.getByText('Enter a valid 6-digit pincode')).toBeInTheDocument()
        expect(screen.getByText('Choose a state or union territory')).toBeInTheDocument()
        expect(createAddress).not.toHaveBeenCalled()
    })

    it('saves a new address that is not a default unless asked', async () => {
        createAddress.mockResolvedValue({ ...OFFICE, id: 'a3' })
        renderWithProviders(<AddressesPage />)
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Add address' }))
        await fillValidForm(user)
        await user.click(screen.getByRole('button', { name: 'Save address' }))

        await waitFor(() =>
            expect(createAddress).toHaveBeenCalledWith({
                label: null,
                recipientName: 'Asha Verma',
                phone: '+919876543210',
                line1: '12 MI Road',
                line2: null,
                city: 'Jaipur',
                state: 'Rajasthan',
                pincode: '302001',
                defaultShipping: false,
                defaultBilling: false,
            }),
        )
        expect(await screen.findByText('Address saved.')).toBeInTheDocument()
    })

    it('makes the first address the default and does not allow switching that off', async () => {
        addresses.mockResolvedValue([])
        createAddress.mockResolvedValue(HOME)
        renderWithProviders(<AddressesPage />)
        const user = userEvent.setup()

        expect(await screen.findByText('You have no saved addresses yet.')).toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: 'Add address' }))
        const shipping = screen.getByRole('checkbox', { name: 'Use as my default shipping address' })
        expect(shipping).toBeChecked()
        expect(shipping).toBeDisabled()

        await fillValidForm(user)
        await user.click(screen.getByRole('button', { name: 'Save address' }))

        await waitFor(() =>
            expect(createAddress).toHaveBeenCalledWith(expect.objectContaining({ defaultShipping: true, defaultBilling: true })),
        )
    })

    it('shows the server message next to the field it is about', async () => {
        createAddress.mockRejectedValue(
            new ApiError(400, 'VALIDATION_FAILED', 'Validation failed.', { pincode: 'must be a valid 6-digit pincode' }),
        )
        renderWithProviders(<AddressesPage />)
        const user = userEvent.setup()

        await user.click(await screen.findByRole('button', { name: 'Add address' }))
        await fillValidForm(user)
        await user.click(screen.getByRole('button', { name: 'Save address' }))

        expect(await screen.findByText('must be a valid 6-digit pincode')).toBeInTheDocument()
    })

    it('stops offering new addresses at the limit of ten', async () => {
        addresses.mockResolvedValue(Array.from({ length: 10 }, (_, index) => ({ ...OFFICE, id: `a${index}`, label: `Place ${index}` })))
        renderWithProviders(<AddressesPage />)

        expect(await screen.findByRole('button', { name: 'Add address' })).toBeDisabled()
        expect(screen.getByText('You can save up to 10 addresses. Delete one to add another.')).toBeInTheDocument()
    })
})