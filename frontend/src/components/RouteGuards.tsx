import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { LoadingState } from './States'

/** Renders child routes only for a logged-in user; otherwise sends them to the login page. */
export function RequireAuth() {
  const { user, initializing } = useAuth()
  const location = useLocation()
  if (initializing) return <LoadingState label="Restoring your session…" />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}

/** Login/register pages: a logged-in user goes straight to the dashboard. */
export function PublicOnly() {
  const { user, initializing } = useAuth()
  if (initializing) return <LoadingState label="Restoring your session…" />
  if (user) return <Navigate to="/dashboard" replace />
  return <Outlet />
}
