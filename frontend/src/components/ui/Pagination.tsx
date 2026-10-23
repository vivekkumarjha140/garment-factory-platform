import { Button } from './Button'

interface PaginationProps {
    /** Zero-based current page. */
    page: number
    totalPages: number
    onChange: (page: number) => void
}

export function Pagination({ page, totalPages, onChange }: PaginationProps) {
    if (totalPages <= 1) return null
    return (
        <nav aria-label="Pagination" className="flex items-center justify-center gap-3 text-sm">
            <Button variant="secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
                Previous
            </Button>
            <span>
                Page {page + 1} of {totalPages}
            </span>
            <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
                Next
            </Button>
        </nav>
    )
}