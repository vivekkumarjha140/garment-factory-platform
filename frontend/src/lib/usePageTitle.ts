import { useEffect } from 'react'

const BASE_TITLE = 'Garment Factory'

export function usePageTitle(title: string | undefined) {
    useEffect(() => {
        document.title = title ? `${title} | ${BASE_TITLE}` : BASE_TITLE
        return () => {
            document.title = BASE_TITLE
        }
    }, [title])
}