import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Icon, type IconName } from './Icon'

const NAV_ITEMS: { to: string; label: string; icon: IconName }[] = [
  { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
  { to: '/applications', label: 'Applications', icon: 'briefcase' },
  { to: '/companies', label: 'Companies', icon: 'building' },
  { to: '/interviews', label: 'Interviews', icon: 'calendar' },
  { to: '/tasks', label: 'Tasks', icon: 'tasks' },
]

export function Layout() {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  // Close the mobile menu after navigating.
  const closeMenu = () => setMenuOpen(false)

  return (
    <div className="app-shell">
      <header className="topbar">
        <button
          type="button"
          className="icon-button"
          onClick={() => setMenuOpen((open) => !open)}
          aria-label={menuOpen ? 'Close menu' : 'Open menu'}
          aria-expanded={menuOpen}
          aria-controls="sidebar"
        >
          <Icon name={menuOpen ? 'close' : 'menu'} />
        </button>
        <span className="brand">
          <img src="/favicon.svg" alt="" width={24} height={24} /> JobTrack
        </span>
      </header>

      <aside id="sidebar" className={`sidebar ${menuOpen ? 'sidebar-open' : ''}`}>
        <div className="brand sidebar-brand">
          <img src="/favicon.svg" alt="" width={26} height={26} /> JobTrack
        </div>
        <nav aria-label="Main">
          <ul className="nav-list">
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  onClick={closeMenu}
                  className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
                >
                  <Icon name={item.icon} />
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="sidebar-footer">
          <NavLink
            to="/profile"
            onClick={closeMenu}
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <Icon name="user" />
            <span className="truncate">{user?.fullName ?? 'Profile'}</span>
          </NavLink>
          <button type="button" className="nav-link" onClick={logout}>
            <Icon name="logout" />
            Log out
          </button>
        </div>
      </aside>
      {menuOpen && <div className="sidebar-scrim" onClick={closeMenu} aria-hidden="true" />}

      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
