import { createBrowserRouter } from 'react-router'
import { AccountPage } from '../features/account/AccountPage'
import { AddressesPage } from '../features/account/AddressesPage'
import { AdminUsersPage } from '../features/admin/AdminUsersPage'
import { RequireAuth, RequireGuest, RequirePermission } from '../features/auth/guards'
import { ForgotPasswordPage } from '../features/auth/pages/ForgotPasswordPage'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { RegisterPage } from '../features/auth/pages/RegisterPage'
import { ResetPasswordPage } from '../features/auth/pages/ResetPasswordPage'
import { VerifyEmailPage } from '../features/auth/pages/VerifyEmailPage'
import { Permission } from '../features/auth/permissions'
import { ProductDetailPage } from '../features/catalog/ProductDetailPage'
import { ProductListPage } from '../features/catalog/ProductListPage'
import { AuditDetailPage } from '../features/inventory/AuditDetailPage'
import { AuditListPage } from '../features/inventory/AuditListPage'
import { RawMaterialsPage } from '../features/inventory/RawMaterialsPage'
import { StockDashboardPage } from '../features/inventory/StockDashboardPage'
import { AppLayout } from '../features/layout/AppLayout'
import { HomePage } from '../features/layout/HomePage'
import { NotFoundPage } from '../features/layout/NotFoundPage'
import { SystemStatusPage } from '../features/system/SystemStatusPage'

export const router = createBrowserRouter([
    { path: '/status', element: <SystemStatusPage /> },
    {
        element: <RequireGuest />,
        children: [
            { path: '/login', element: <LoginPage /> },
            { path: '/register', element: <RegisterPage /> },
        ],
    },
    { path: '/verify-email', element: <VerifyEmailPage /> },
    { path: '/forgot-password', element: <ForgotPasswordPage /> },
    { path: '/reset-password', element: <ResetPasswordPage /> },
    {
        element: <AppLayout />,
        children: [
            // The shop is public: anyone can browse without signing in.
            { index: true, element: <HomePage /> },
            { path: '/shop', element: <ProductListPage /> },
            { path: '/shop/:slug', element: <ProductDetailPage /> },
            {
                element: <RequireAuth />,
                children: [
                    { path: '/account', element: <AccountPage /> },
                    { path: '/account/addresses', element: <AddressesPage /> },
                    {
                        // UX only: the backend enforces the same permission on every call.
                        element: <RequirePermission permission={Permission.USER_VIEW} />,
                        children: [{ path: '/admin/users', element: <AdminUsersPage /> }],
                    },
                    {
                        element: <RequirePermission permission={Permission.STOCK_VIEW} />,
                        children: [
                            { path: '/admin/inventory', element: <StockDashboardPage /> },
                            { path: '/admin/inventory/raw-materials', element: <RawMaterialsPage /> },
                        ],
                    },
                    {
                        element: <RequirePermission permission={Permission.INVENTORY_AUDIT} />,
                        children: [
                            { path: '/admin/inventory/audits', element: <AuditListPage /> },
                            { path: '/admin/inventory/audits/:publicId', element: <AuditDetailPage /> },
                        ],
                    },
                ],
            },
        ],
    },
    { path: '*', element: <NotFoundPage /> },
])