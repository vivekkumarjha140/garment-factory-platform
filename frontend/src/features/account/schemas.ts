import { z } from 'zod'

/** Mirrors the backend rules (the backend remains the authority). */
export const profileSchema = z.object({
    fullName: z.string().trim().min(1, 'Enter your full name').max(120, 'Name is too long'),
    phone: z.string().trim().regex(/^(\+?[0-9]{7,15})?$/, 'Use 7 to 15 digits, optionally starting with +'),
    marketingOptIn: z.boolean(),
})
export type ProfileFormValues = z.infer<typeof profileSchema>

export const addressSchema = z.object({
    label: z.string().trim().max(30, 'Use at most 30 characters'),
    recipientName: z.string().trim().min(1, 'Enter the recipient name').max(120, 'Name is too long'),
    phone: z.string().trim().regex(/^\+?[0-9]{7,15}$/, 'Use 7 to 15 digits, optionally starting with +'),
    line1: z.string().trim().min(1, 'Enter the house, flat or street').max(200, 'Use at most 200 characters'),
    line2: z.string().trim().max(200, 'Use at most 200 characters'),
    city: z.string().trim().min(1, 'Enter the city').max(80, 'Use at most 80 characters'),
    state: z.string().min(1, 'Choose a state or union territory'),
    pincode: z.string().trim().regex(/^[1-9][0-9]{5}$/, 'Enter a valid 6-digit pincode'),
    defaultShipping: z.boolean(),
    defaultBilling: z.boolean(),
})
export type AddressFormValues = z.infer<typeof addressSchema>