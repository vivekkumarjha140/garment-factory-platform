interface QuantityStepperProps {
    /** Product name, used in the accessible names of the buttons. */
    label: string
    value: number
    canDecrease: boolean
    canIncrease: boolean
    disabled?: boolean
    onDecrease: () => void
    onIncrease: () => void
}

const STEP_BUTTON =
    'h-8 w-8 text-lg leading-none hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-slate-800'

export function QuantityStepper({ label, value, canDecrease, canIncrease, disabled = false, onDecrease, onIncrease }: QuantityStepperProps) {
    return (
        <div role="group" aria-label={`Quantity of ${label}`} className="inline-flex items-center rounded-md border border-slate-300 dark:border-slate-700">
            <button
                type="button"
                aria-label={`Decrease quantity of ${label}`}
                disabled={disabled || !canDecrease}
                onClick={onDecrease}
                className={`${STEP_BUTTON} rounded-l-md`}
            >
                −
            </button>
            <span aria-live="polite" className="min-w-10 px-2 text-center text-sm">
                {value}
            </span>
            <button
                type="button"
                aria-label={`Increase quantity of ${label}`}
                disabled={disabled || !canIncrease}
                onClick={onIncrease}
                className={`${STEP_BUTTON} rounded-r-md`}
            >
                +
            </button>
        </div>
    )
}