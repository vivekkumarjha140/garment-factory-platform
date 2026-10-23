import axios from 'axios'

/**
 * X-Requested-With is required by the backend on /auth/refresh and /auth/logout: a cross-site form
 * cannot add a custom header, so it cannot forge those calls with the refresh cookie.
 */
export function createHttp() {
    return axios.create({
        baseURL: '/api/v1',
        timeout: 15_000,
        withCredentials: true,
        headers: { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
    })
}