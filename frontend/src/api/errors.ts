import { isAxiosError } from 'axios'

/** A failed API call in one predictable shape, built from the backend's RFC 9457 problem body. */
export class ApiError extends Error {
    readonly status: number
    readonly code: string
    readonly fieldErrors: Record<string, string>
    readonly retryAfterSeconds: number | undefined

    constructor(
        status: number,
        code: string,
        message: string,
        fieldErrors: Record<string, string> = {},
        retryAfterSeconds?: number,
    ) {
        super(message)
        this.name = 'ApiError'
        this.status = status
        this.code = code
        this.fieldErrors = fieldErrors
        this.retryAfterSeconds = retryAfterSeconds
    }
}

interface ProblemBody {
    code?: string
    detail?: string
    errors?: Record<string, string>
}

export function toApiError(error: unknown): ApiError {
    if (error instanceof ApiError) return error
    if (isAxiosError(error)) {
        if (!error.response) {
            return new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check your connection and try again.')
        }
        const body = error.response.data as ProblemBody | undefined
        const retryAfter = Number(error.response.headers['retry-after'])
        return new ApiError(
            error.response.status,
            body?.code ?? 'UNKNOWN_ERROR',
            body?.detail ?? 'Something went wrong. Please try again.',
            body?.errors ?? {},
            Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
        )
    }
    return new ApiError(0, 'UNKNOWN_ERROR', 'Something went wrong. Please try again.')
}

/** The text to show a person. Rate-limit errors say how long to wait. */
export function userMessage(error: ApiError): string {
    if (error.status === 429) {
        const wait = error.retryAfterSeconds
        if (!wait) return 'Too many attempts. Please try again later.'
        return `Too many attempts. Please try again in ${wait < 90 ? `${wait} seconds` : `${Math.ceil(wait / 60)} minutes`}.`
    }
    return error.message
}