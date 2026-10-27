import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime } from './format'

describe('date formatting', () => {
    it('shows date and time in Indian time', () => {
        expect(formatDateTime('2026-10-26T10:00:00Z')).toMatch(/^26 Oct 2026, 3:30\s?(am|pm)$/i)
    })

    it('shows the Indian date even when UTC is still the day before', () => {
        expect(formatDate('2026-12-31T19:00:00Z')).toBe('1 Jan 2027')
    })
})