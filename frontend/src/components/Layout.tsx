import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Icon, type IconName } from './Icon'

const NAV_ITEMS: { to: string; label: string; icon: IconName }[] = [
  { to: '/dashboard', label: 'Overview', icon: 'dashboard' },
  { to: '/applications', label: 'Applications', icon: 'briefcase' },
  { to: '/companies', label: 'Companies', icon: 'building' },
  { to: '/interviews', label: 'Interviews', icon: 'calendar' },
]

/**
 * App shell: a sidebar on the left (brand, pages, account) and the page beside it. On phones the
 * sidebar becomes a slim top bar plus a tab bar along the bottom.
 */
export function Layout() {
  const { user, logout } = useAuth()
  const navClass = ({ isActive }: { isActive: boolean }) => `side-link ${isActive ? 'active' : ''}`
  const initials =
    user?.fullName
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('') || '?'

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink to="/dashboard" className="sidebar-brand" aria-label="JobTrack home">
          <img src={`${import.meta.env.BASE_URL}favicon.svg`} alt="" width={28} height={28} />
          <span>JobTrack</span>
        </NavLink>

        <nav aria-label="Main" className="sidebar-nav">
          <p className="sidebar-heading">Workspace</p>
          <ul>
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} className={navClass}>
                  <Icon name={item.icon} size={18} />
                  <span>{item.label}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className="sidebar-account">
          <NavLink to="/profile" className={({ isActive }) => `account-link ${isActive ? 'active' : ''}`}>
            <span className="avatar" aria-hidden="true">
              {initials}
            </span>
            <span className="account-text">
              <span className="account-name">{user?.fullName ?? 'Profile'}</span>
              <span className="account-email">{user?.email}</span>
            </span>
          </NavLink>
          <button type="button" className="icon-button" onClick={logout} aria-label="Log out" title="Log out">
            <Icon name="logout" />
          </button>
        </div>
      </aside>

      {/* Phones: brand and account on top; the pages are in the tab bar below. */}
      <header className="mobile-bar">
        <NavLink to="/dashboard" className="sidebar-brand" aria-label="JobTrack home">
          <img src={`${import.meta.env.BASE_URL}favicon.svg`} alt="" width={24} height={24} />
          <span>JobTrack</span>
        </NavLink>
        <div className="mobile-bar-actions">
          <NavLink to="/profile" className="avatar avatar-small" aria-label="Profile" title="Profile">
            {initials}
          </NavLink>
          <button type="button" className="icon-button" onClick={logout} aria-label="Log out" title="Log out">
            <Icon name="logout" />
          </button>
        </div>
      </header>

      <main className="main">
        <Outlet />
      </main>

      <nav aria-label="Main" className="tab-bar">
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => `tab-bar-link ${isActive ? 'active' : ''}`}>
            <Icon name={item.icon} size={20} />
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
