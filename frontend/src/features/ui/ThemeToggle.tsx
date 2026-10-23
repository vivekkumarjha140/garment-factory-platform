import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '../../app/hooks'
import { toggleTheme } from './uiSlice'

export function ThemeToggle() {
    const theme = useAppSelector((state) => state.ui.theme)
    const dispatch = useAppDispatch()

    useEffect(() => {
        document.documentElement.classList.toggle('dark', theme === 'dark')
        try {
            localStorage.setItem('gf.theme', theme)
        } catch {
            // Storage unavailable; the theme simply is not remembered.
        }
    }, [theme])

    const next = theme === 'dark' ? 'light' : 'dark'
    return (
        <button
            type="button"
            onClick={() => dispatch(toggleTheme())}
            aria-label={`Switch to ${next} theme`}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
        >
            {next === 'dark' ? 'Dark mode' : 'Light mode'}
        </button>
    )
}