import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Icon } from './Icon'

const NAV_ITEMS: { to: string; label: string }[] = [
  { to: '/dashboard', label: 'Overview' },
  { to: '/applications', label: 'Applications' },
  { to: '/companies', label: 'Companies' },
  { to: '/interviews', label: 'Interviews' },
]

/** App shell: a slim translucent bar across the top (apple.com style) and a centred page. */
export function Layout() {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)

  const navClass = ({ isActive }: { isActive: boolean }) => `nav-link ${isActive ? 'active' : ''}`
  const initial = user?.fullName.trim().charAt(0).toUpperCase() ?? '?'

  return (
    <div className="app-shell">
      <header className={`globalnav ${menuOpen ? 'globalnav-open' : ''}`}>
        <div className="globalnav-inner">
          <NavLink to="/dashboard" className="brand" aria-label="JobTrack home">
            <img src="/favicon.svg" alt="" width={20} height={20} />
            <span>JobTrack</span>
          </NavLink>

          {/* Any click on a link inside closes the mobile menu. */}
          <nav aria-label="Main" id="main-nav" className="globalnav-links" onClick={() => setMenuOpen(false)}>
            <ul className="nav-list">
              {NAV_ITEMS.map((item) => (
                <li key={item.to}>
                  <NavLink to={item.to} className={navClass}>
                    {item.label}
                  </NavLink>
                </li>
              ))}
              <li className="nav-mobile-only">
                <NavLink to="/profile" className={navClass}>
                  Profile
                </NavLink>
              </li>
              <li className="nav-mobile-only">
                <button type="button" className="nav-link" onClick={logout}>
                  Log out
                </button>
              </li>
            </ul>
          </nav>

          <div className="globalnav-actions">
            <NavLink
              to="/profile"
              className="avatar-link"
              title={user?.fullName ?? 'Profile'}
              aria-label="Profile"
              onClick={() => setMenuOpen(false)}
            >
              {initial}
            </NavLink>
            <button type="button" className="nav-link nav-desktop-only" onClick={logout}>
              Log out
            </button>
            <button
              type="button"
              className="icon-button menu-toggle"
              onClick={() => setMenuOpen((open) => !open)}
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={menuOpen}
              aria-controls="main-nav"
            >
              <Icon name={menuOpen ? 'close' : 'menu'} />
            </button>
          </div>
        </div>
      </header>

      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
