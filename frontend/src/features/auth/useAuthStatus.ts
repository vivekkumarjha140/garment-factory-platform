import { useAppSelector } from '../../app/hooks'

/** 'unknown' while the session is being restored, then 'authenticated' or 'anonymous'. */
export function useAuthStatus() {
    return useAppSelector((state) => state.auth.status)
}