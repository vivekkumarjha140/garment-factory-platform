import type { AxiosResponse } from 'axios'
import { toApiError } from './errors'

/** Runs a request and returns its body; any failure becomes an ApiError. */
export async function call<T>(request: Promise<AxiosResponse<T>>): Promise<T> {
    try {
        return (await request).data
    } catch (error) {
        throw toApiError(error)
    }
}

/** Same, for endpoints that answer 204 No Content. */
export async function callVoid(request: Promise<AxiosResponse<unknown>>): Promise<void> {
    try {
        await request
    } catch (error) {
        throw toApiError(error)
    }
}