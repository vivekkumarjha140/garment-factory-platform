import { ApiError, toApiError, userMessage } from '../../api/errors'

/** A sentence to show a person for anything that can go wrong around payment. */
export function errorText(caught: unknown): string {
    if (caught instanceof ApiError) return userMessage(caught)
    if (caught instanceof Error && caught.message) return caught.message
    return userMessage(toApiError(caught))
}