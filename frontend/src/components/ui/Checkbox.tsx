import { useId, type ComponentProps, type ReactNode } from 'react'

type CheckboxProps = Omit<ComponentProps<'input'>, 'type'> & { label: ReactNode; hint?: string }

export function Checkbox({ label, hint, id, className = '', ...props }: CheckboxProps) {
    const generatedId = useId()
    const fieldId = id ?? generatedId
    const hintId = `${fieldId}-hint`

    return (
        <div className="space-y-0.5">
            <div className="flex items-center gap-2">
                <input
                    id={fieldId}
                    type="checkbox"
                    aria-describedby={hint ? hintId : undefined}
                    className={`h-4 w-4 rounded border-slate-300 text-indigo-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 dark:border-slate-600 ${className}`}
                    {...props}
                />
                <label htmlFor={fieldId} className="text-sm">
                    {label}
                </label>
            </div>
            {hint && (
                <p id={hintId} className="pl-6 text-xs text-slate-500 dark:text-slate-400">
                    {hint}
                </p>
            )}
        </div>
    )
}