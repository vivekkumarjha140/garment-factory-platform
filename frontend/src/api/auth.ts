import type { AuthResponse, MessageResponse, SessionInfo } from '../types/auth'
import { call, callVoid } from './call'
import { apiClient } from './client'

export interface RegisterBody {
    fullName: string
    email: string
    password: string
    phone?: string
}

export interface LoginBody {
    email: string
    password: string
}

export const authApi = {
    register: (body: RegisterBody) => call(apiClient.post<MessageResponse>('/auth/register', body)),
    verifyEmail: (token: string) => callVoid(apiClient.post('/auth/verify-email', { token })),
    resendVerification: (email: string) =>
        call(apiClient.post<MessageResponse>('/auth/resend-verification', { email })),
    login: (body: LoginBody) => call(apiClient.post<AuthResponse>('/auth/login', body)),
    logout: () => callVoid(apiClient.post('/auth/logout')),
    forgotPassword: (email: string) => call(apiClient.post<MessageResponse>('/auth/forgot-password', { email })),
    resetPassword: (token: string, newPassword: string) =>
        callVoid(apiClient.post('/auth/reset-password', { token, newPassword })),
    listSessions: () => call(apiClient.get<SessionInfo[]>('/auth/sessions')),
    revokeSession: (id: string) => callVoid(apiClient.delete(`/auth/sessions/${encodeURIComponent(id)}`)),
    logoutAll: () => callVoid(apiClient.post('/auth/logout-all')),
    changePassword: (currentPassword: string, newPassword: string) =>
        callVoid(apiClient.post('/auth/change-password', { currentPassword, newPassword })),
}