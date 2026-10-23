import { usePageTitle } from '../../lib/usePageTitle'
import { usePermissions } from '../auth/usePermissions'
import { AccountTabs } from './AccountTabs'
import { ChangePasswordForm } from './ChangePasswordForm'
import { ProfileForm } from './ProfileForm'
import { SessionsPanel } from './SessionsPanel'

export function AccountPage() {
    const { user } = usePermissions()
    usePageTitle('Your account')
    if (!user) return null

    return (
        <div className="space-y-8">
            <div className="space-y-4">
                <h1 className="text-2xl font-semibold">Your account</h1>
                <AccountTabs />
            </div>
            <ProfileForm />
            <p className="text-sm text-slate-600 dark:text-slate-400">Roles: {user.roles.join(', ') || 'None'}</p>
            <ChangePasswordForm />
            <SessionsPanel />
        </div>
    )
}