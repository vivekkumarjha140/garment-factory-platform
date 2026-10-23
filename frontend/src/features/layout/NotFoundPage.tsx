import { Link } from 'react-router'

export function NotFoundPage() {
    return (
        <main className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center gap-3 p-6 text-center">
            <h1 className="text-2xl font-semibold">Page not found</h1>
            <p className="text-sm text-slate-600 dark:text-slate-400">The page you are looking for does not exist.</p>
            <Link to="/" className="text-sm text-indigo-600 underline dark:text-indigo-400">
                Go to the home page
            </Link>
        </main>
    )
}