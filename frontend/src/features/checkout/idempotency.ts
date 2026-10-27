function newKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
    return `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}

/**
 * One Idempotency-Key per distinct checkout request. Retrying the SAME request (a timeout, a double click) reuses
 * the key so the server returns the same order; changing the address or payment method needs a new key,
 * because the server rejects a key that is reused for a different request.
 */
export function createKeyHolder() {
    let fingerprint: string | null = null
    let key = ''
    return {
        keyFor(next: string): string {
            if (fingerprint !== next) {
                fingerprint = next
                key = newKey()
            }
            return key
        },
        reset() {
            fingerprint = null
            key = ''
        },
    }
}