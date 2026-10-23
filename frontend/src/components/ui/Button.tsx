import type { ComponentProps } from 'react'

type Variant = 'primary' | 'secondary' | 'danger'

const VARIANTS: Record<Variant, string> = {
    primary: 'bg-indigo-600 text-white hover:bg-indigo-700 focus-visible:ring-indigo-400',
    secondary:
        'border border-slate-300 text-slate-700 hover:bg-slate-100 focus-visible:ring-indigo-400 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800',
    danger: 'bg-red-600 text-white hover:bg-red-700 focus-visible:ring-red-400',
}

type ButtonProps = ComponentProps<'button'> & { variant?: Variant; loading?: boolean }

export function Button({ variant = 'primary', loading = false, disabled, className = '', children, ...props }: ButtonProps) {
    return (
        <button
            type="button"
            disabled={disabled || loading}
            aria-busy={loading || undefined}
            className={`inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium focus:outline-none focus-visible:ring-2 disabled:cursor-not-allowed disabled:opacity-60 ${VARIANTS[variant]} ${className}`}
            {...props}
        >
            {loading ? 'Please wait…' : children}
        </button>
    )
}