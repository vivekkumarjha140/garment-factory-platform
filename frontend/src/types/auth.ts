export interface UserSummary {
    publicId: string
    email: string
    fullName: string
    roles: string[]
    permissions: string[]
}

export interface AuthResponse {
    accessToken: string
    tokenType: string
    expiresIn: number
    user: UserSummary
}

export interface SessionInfo {
    id: string
    userAgent: string | null
    ip: string | null
    lastActiveAt: string
    current: boolean
}

export interface MessageResponse {
    message: string
}

export interface PageResponse<T> {
    items: T[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}

export interface AdminUser {
    publicId: string
    email: string
    fullName: string
    phone: string | null
    status: string
    roles: string[]
    emailVerifiedAt: string | null
    lastLoginAt: string | null
    createdAt: string
}