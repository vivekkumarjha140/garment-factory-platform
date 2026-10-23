export interface CustomerProfile {
    fullName: string
    email: string
    phone: string | null
    customerType: string
    marketingOptIn: boolean
}

/** PATCH semantics: a missing field is unchanged and an empty phone clears it. */
export interface UpdateProfileBody {
    fullName?: string
    phone?: string
    marketingOptIn?: boolean
}

export interface Address {
    id: string
    label: string | null
    recipientName: string
    phone: string
    line1: string
    line2: string | null
    city: string
    state: string
    pincode: string
    defaultShipping: boolean
    defaultBilling: boolean
}

/** defaultShipping / defaultBilling only ever mean "make this the default" when true. */
export interface AddressBody {
    label: string | null
    recipientName: string
    phone: string
    line1: string
    line2: string | null
    city: string
    state: string
    pincode: string
    defaultShipping: boolean
    defaultBilling: boolean
}

export type DefaultKind = 'shipping' | 'billing'