import type { AdminUser, PageResponse } from '../types/auth'
import { call } from './call'
import { apiClient } from './client'

export interface UserQuery {
    q: string
    page: number
    size: number
}

export const adminApi = {
    listUsers: (query: UserQuery) =>
        call(
            apiClient.get<PageResponse<AdminUser>>('/admin/users', {
                params: { q: query.q || undefined, page: query.page, size: query.size },
            }),
        ),
}