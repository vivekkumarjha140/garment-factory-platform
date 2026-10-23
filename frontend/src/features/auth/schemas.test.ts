import { describe, expect, it } from 'vitest'
import { changePasswordSchema, loginSchema, passwordSchema, registerSchema } from './schemas'

describe('passwordSchema', () => {
    it('accepts a strong password', () => {
        expect(passwordSchema.safeParse('Str0ngPassw0rd').success).toBe(true)
    })

    it.each([
        ['too short', 'Short1'],
        ['too long', 'a1'.repeat(33)],
        ['no digit', 'onlyletterspassword'],
        ['no letter', '1234567890123'],
        ['over BCrypt byte limit', 'あ'.repeat(39) + '1'],
    ])('rejects a password that is %s', (_name, password) => {
        expect(passwordSchema.safeParse(password).success).toBe(false)
    })
})

describe('loginSchema', () => {
    it('trims the email and requires both fields', () => {
        const parsed = loginSchema.safeParse({ email: '  user@example.com ', password: 'x' })
        expect(parsed.success && parsed.data.email).toBe('user@example.com')
        expect(loginSchema.safeParse({ email: '', password: '' }).success).toBe(false)
    })
})

describe('registerSchema', () => {
    const valid = {
        fullName: 'Test User',
        email: 'test@example.com',
        phone: '',
        password: 'Str0ngPassw0rd',
        confirmPassword: 'Str0ngPassw0rd',
    }

    it('accepts valid input with an empty optional phone', () => {
        expect(registerSchema.safeParse(valid).success).toBe(true)
    })

    it('reports a mismatch on the confirmation field', () => {
        const parsed = registerSchema.safeParse({ ...valid, confirmPassword: 'Different1234' })
        expect(parsed.success).toBe(false)
        expect(!parsed.success && parsed.error.issues[0]?.path).toEqual(['confirmPassword'])
    })

    it('rejects a malformed phone number', () => {
        expect(registerSchema.safeParse({ ...valid, phone: '12-34' }).success).toBe(false)
    })
})

describe('changePasswordSchema', () => {
    it('rejects a new password equal to the current one', () => {
        const parsed = changePasswordSchema.safeParse({
            currentPassword: 'Str0ngPassw0rd',
            newPassword: 'Str0ngPassw0rd',
            confirmPassword: 'Str0ngPassw0rd',
        })
        expect(parsed.success).toBe(false)
    })
})