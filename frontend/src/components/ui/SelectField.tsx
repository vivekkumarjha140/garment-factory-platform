import { useId, type ComponentProps } from 'react'

type SelectFieldProps = ComponentProps<'select'> & { label: string; error?: string; hint?: string; hideLabel?: boolean }

/** Labelled drop-down that announces its error and hint to screen readers. */
export function SelectField({ label, error, hint, hideLabel = false, id, className = '', children, ...props }: SelectFieldProps) {
    const generatedId = useId()
    const fieldId = id ?? generatedId
    const errorId = `${fieldId}-error`
    const hintId = `${fieldId}-hint`
    const describedBy = error ? errorId : hint ? hintId : undefined

    return (
        <div className="space-y-1">
            <label htmlFor={fieldId} className={hideLabel ? 'sr-only' : 'block text-sm font-medium'}>
                {label}
            </label>
            <select
                id={fieldId}
                aria-invalid={error ? true : undefined}
                aria-describedby={describedBy}
                className={`block w-full rounded-md border bg-white px-3 py-2 text-sm shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 dark:bg-slate-950 ${error ? 'border-red-500' : 'border-slate-300 dark:border-slate-700'} ${className}`}
                {...props}
            >
                {children}
            </select>
            {hint && !error && (
                <p id={hintId} className="text-xs text-slate-500 dark:text-slate-400">
                    {hint}
                </p>
            )}
            {error && (
                <p id={errorId} className="text-xs text-red-600 dark:text-red-400">
                    {error}
                </p>
            )}
        </div>
    )
}