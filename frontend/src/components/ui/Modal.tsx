import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

const FOCUSABLE =
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

interface ModalProps {
    title: string
    onClose: () => void
    /** A wider panel for tables. */
    wide?: boolean
    children: ReactNode
}

export function Modal({ title, onClose, wide = false, children }: ModalProps) {
    const titleId = useId()
    const panel = useRef<HTMLDivElement>(null)
    // Read during the first render, before any field inside the dialog can take focus.
    const opener = useRef<Element | null>(document.activeElement)

    useEffect(() => {
        const element = panel.current
        const previous = opener.current
        if (element && !element.contains(document.activeElement)) {
            ;(element.querySelector<HTMLElement>(FOCUSABLE) ?? element).focus()
        }
        return () => {
            if (previous instanceof HTMLElement) previous.focus()
        }
    }, [])

    function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        if (event.key === 'Escape') {
            event.stopPropagation()
            onClose()
            return
        }
        if (event.key !== 'Tab' || !panel.current) return
        const focusable = panel.current.querySelectorAll<HTMLElement>(FOCUSABLE)
        if (focusable.length === 0) return
        const first = focusable[0]
        const last = focusable[focusable.length - 1]
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault()
            last.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault()
            first.focus()
        }
    }

    return createPortal(
        <div
            className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-slate-900/50 p-4 sm:items-center"
            onKeyDown={handleKeyDown}
        >
            <div
                ref={panel}
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
                tabIndex={-1}
                className={`w-full ${wide ? 'max-w-4xl' : 'max-w-md'} rounded-lg bg-white p-5 shadow-xl dark:bg-slate-900`}
            >
                <div className="mb-4 flex items-start justify-between gap-3">
                    <h2 id={titleId} className="text-lg font-semibold">
                        {title}
                    </h2>
                    <button
                        type="button"
                        onClick={onClose}
                        className="rounded-md px-2 py-1 text-sm text-slate-600 hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 dark:text-slate-300 dark:hover:bg-slate-800"
                    >
                        Close
                    </button>
                </div>
                {children}
            </div>
        </div>,
        document.body,
    )
}