import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { toApiError, type ApiError } from '../../api/errors'
import { applyFieldErrors } from '../auth/formErrors'

/** Backend field messages go next to their fields; anything else goes to the form-level alert. */
export function reportSubmitError<T extends FieldValues>(
    caught: unknown,
    setFieldError: UseFormSetError<T>,
    fields: ReadonlyArray<Path<T>>,
    setFormError: (error: ApiError) => void,
): void {
    const apiError = toApiError(caught)
    const matchesAField = Object.keys(apiError.fieldErrors).some((key) => fields.some((field) => field === key))
    if (matchesAField) {
        applyFieldErrors(apiError.fieldErrors, setFieldError, fields)
    } else {
        setFormError(apiError)
    }
}