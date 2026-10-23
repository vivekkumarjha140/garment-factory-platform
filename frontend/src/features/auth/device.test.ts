import { describe, expect, it } from 'vitest'
import { describeUserAgent } from './device'

describe('describeUserAgent', () => {
    it.each([
        [
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36',
            'Chrome on Windows',
        ],
        [
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36 Edg/126.0',
            'Edge on Windows',
        ],
        [
            'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
            'Safari on iOS',
        ],
        ['Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36', 'Chrome on Android'],
    ])('describes %s', (agent, expected) => {
        expect(describeUserAgent(agent)).toBe(expected)
    })

    it('falls back for missing or unrecognised agents', () => {
        expect(describeUserAgent(null)).toBe('Unknown device')
        expect(describeUserAgent('Java-http-client/21')).toBe('Unknown device')
    })
})