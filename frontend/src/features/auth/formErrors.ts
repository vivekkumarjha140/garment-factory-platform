import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'

/** Shows backend validation messages next to the matching form fields. */
export function applyFieldErrors<T extends FieldValues>(
    errors: Record<string, string>,
    setError: UseFormSetError<T>,
    fields: ReadonlyArray<Path<T>>,
): void {
    for (const [field, message] of Object.entries(errors)) {
        const match = fields.find((candidate) => candidate === field)
        if (match) setError(match, { type: 'server', message })
    }
}