import { describe, expect, it } from 'vitest'
import { createKeyHolder } from './idempotency'

describe('createKeyHolder', () => {
    it('gives the same key when the same request is retried', () => {
        const keys = createKeyHolder()
        expect(keys.keyFor('a1|ONLINE')).toBe(keys.keyFor('a1|ONLINE'))
    })

    it('gives a new key when the address or payment method changes', () => {
        const keys = createKeyHolder()
        const first = keys.keyFor('a1|ONLINE')
        expect(keys.keyFor('a1|COD')).not.toBe(first)
        expect(keys.keyFor('a2|COD')).not.toBe(keys.keyFor('a1|COD'))
    })

    it('starts over after a reset', () => {
        const keys = createKeyHolder()
        const first = keys.keyFor('a1|ONLINE')
        keys.reset()
        expect(keys.keyFor('a1|ONLINE')).not.toBe(first)
    })

    it('makes keys the backend accepts (8-64 letters, digits, - or _)', () => {
        expect(createKeyHolder().keyFor('x')).toMatch(/^[A-Za-z0-9_-]{8,64}$/)
    })
})