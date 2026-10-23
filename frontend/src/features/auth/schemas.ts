import { z } from 'zod'

const encoder = new TextEncoder()

/** Mirrors the backend password policy (the backend remains the authority). */
export const passwordSchema = z
    .string()
    .min(10, 'Use at least 10 characters')
    .max(64, 'Use at most 64 characters')
    .regex(/\p{L}/u, 'Include at least one letter')
    .regex(/\p{Nd}/u, 'Include at least one digit')
    .refine((value) => encoder.encode(value).length <= 72, 'This password is too long once encoded; use fewer special characters')

const emailSchema = z
    .string()
    .trim()
    .min(1, 'Enter your email address')
    .max(254, 'Email address is too long')
    .email('Enter a valid email address')

export const loginSchema = z.object({
    email: emailSchema,
    password: z.string().min(1, 'Enter your password'),
})
export type LoginForm = z.infer<typeof loginSchema>

export const emailFormSchema = z.object({ email: emailSchema })
export type EmailForm = z.infer<typeof emailFormSchema>

export const registerSchema = z
    .object({
        fullName: z.string().trim().min(1, 'Enter your full name').max(120, 'Name is too long'),
        email: emailSchema,
        phone: z.string().trim().regex(/^(\+?[0-9]{7,15})?$/, 'Use 7 to 15 digits, optionally starting with +'),
        password: passwordSchema,
        confirmPassword: z.string(),
    })
    .refine((data) => data.password === data.confirmPassword, {
        message: 'Passwords do not match',
        path: ['confirmPassword'],
    })
export type RegisterForm = z.infer<typeof registerSchema>

export const resetPasswordSchema = z
    .object({ newPassword: passwordSchema, confirmPassword: z.string() })
    .refine((data) => data.newPassword === data.confirmPassword, {
        message: 'Passwords do not match',
        path: ['confirmPassword'],
    })
export type ResetPasswordForm = z.infer<typeof resetPasswordSchema>

export const changePasswordSchema = z
    .object({
        currentPassword: z.string().min(1, 'Enter your current password'),
        newPassword: passwordSchema,
        confirmPassword: z.string(),
    })
    .refine((data) => data.newPassword === data.confirmPassword, {
        message: 'Passwords do not match',
        path: ['confirmPassword'],
    })
    .refine((data) => data.newPassword !== data.currentPassword, {
        message: 'Choose a password different from the current one',
        path: ['newPassword'],
    })
export type ChangePasswordForm = z.infer<typeof changePasswordSchema>