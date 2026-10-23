import { useAppSelector } from '../../app/hooks'

/**
 * For showing or hiding parts of the UI only. Hiding a button is not security: the backend
 * checks the same permission on every request.
 */
export function usePermissions() {
    const user = useAppSelector((state) => state.auth.user)
    return {
        user,
        can: (permission: string): boolean => user?.permissions.includes(permission) ?? false,
        hasRole: (role: string): boolean => user?.roles.includes(role) ?? false,
    }
}