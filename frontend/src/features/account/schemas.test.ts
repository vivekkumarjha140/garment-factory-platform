import { describe, expect, it } from 'vitest'
import { addressSchema, profileSchema } from './schemas'

const VALID_ADDRESS = {
    label: 'Home',
    recipientName: 'Asha Verma',
    phone: '+919876543210',
    line1: '12 MI Road',
    line2: '',
    city: 'Jaipur',
    state: 'Rajasthan',
    pincode: '302001',
    defaultShipping: false,
    defaultBilling: false,
}

function addressMessages(overrides: Partial<typeof VALID_ADDRESS>): string[] {
    const result = addressSchema.safeParse({ ...VALID_ADDRESS, ...overrides })
    return result.success ? [] : result.error.issues.map((issue) => issue.message)
}

describe('profileSchema', () => {
    it('accepts a name with no phone, or with a valid one', () => {
        expect(profileSchema.safeParse({ fullName: 'Asha', phone: '', marketingOptIn: false }).success).toBe(true)
        expect(profileSchema.safeParse({ fullName: 'Asha', phone: '+919876543210', marketingOptIn: true }).success).toBe(true)
    })

    it('rejects a blank name and a malformed phone', () => {
        expect(profileSchema.safeParse({ fullName: '   ', phone: '', marketingOptIn: false }).success).toBe(false)
        expect(profileSchema.safeParse({ fullName: 'Asha', phone: '12-34', marketingOptIn: false }).success).toBe(false)
    })
})

describe('addressSchema', () => {
    it('accepts a complete address and trims the text', () => {
        const result = addressSchema.safeParse({ ...VALID_ADDRESS, recipientName: '  Asha Verma  ' })

        expect(result.success).toBe(true)
        if (result.success) expect(result.data.recipientName).toBe('Asha Verma')
    })

    it('rejects a pincode that is not six digits or starts with 0', () => {
        expect(addressMessages({ pincode: '012345' })).toEqual(['Enter a valid 6-digit pincode'])
        expect(addressMessages({ pincode: '30200' })).toEqual(['Enter a valid 6-digit pincode'])
    })

    it('rejects a missing state, recipient, city, street and a malformed phone', () => {
        expect(addressMessages({ state: '' })).toEqual(['Choose a state or union territory'])
        expect(addressMessages({ recipientName: ' ' })).toEqual(['Enter the recipient name'])
        expect(addressMessages({ city: '' })).toEqual(['Enter the city'])
        expect(addressMessages({ line1: '' })).toEqual(['Enter the house, flat or street'])
        expect(addressMessages({ phone: '12-34' })).toEqual(['Use 7 to 15 digits, optionally starting with +'])
    })
})