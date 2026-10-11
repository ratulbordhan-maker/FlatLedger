import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { useApi } from '../lib/useApi.js'
import { Icons, LeafMark } from './Art.jsx'
import MonthSwitcher from './MonthSwitcher.jsx'

/**
 * The frame around every page: app name, month switcher, who is signed in,
 * and the section tabs (along the top on laptops, along the bottom on phones).
 */
export default function Layout() {
  const { session, isManager, logout } = useAuth()
  const { year, month } = useParams()
  const location = useLocation()
  const months = useApi('/months')
  const { reload } = months

  // Reload the month list when moving to another month, so a month that was
  // just opened (or closed) shows up with the right status.
  useEffect(() => {
    if (year && month) reload()
  }, [year, month, reload])

  // Pages fire this after opening, closing or reopening a month
  useEffect(() => {
    window.addEventListener('flatmeals:months-changed', reload)
    return () => window.removeEventListener('flatmeals:months-changed', reload)
  }, [reload])

  const inMonth = Boolean(year && month)
  const base = inMonth ? `/m/${year}/${month}` : null
  const section = location.pathname.split('/')[4] || 'dashboard'

  const tabs = [
    { to: 'dashboard', label: 'Overview', icon: Icons.overview },
    { to: 'meals', label: 'Meals', icon: Icons.meals },
    { to: 'expenses', label: 'Expenses', icon: Icons.expenses },
    ...(isManager
      ? [
          { to: 'payments', label: 'Payments', icon: Icons.payments },
          { to: 'cash', label: 'Cash', icon: Icons.cash },
          { to: 'members', label: 'Members', icon: Icons.members },
        ]
      : [{ to: 'account', label: 'My account', icon: Icons.account }]),
  ]

  return (
    <div className="app">
      <header className="topbar">
        <Link to="/" className="brand">
          <LeafMark className="brand-mark" />
          FlatLedger
        </Link>
        {inMonth && (
          <MonthSwitcher months={months.data || []} year={Number(year)} month={Number(month)} section={section} />
        )}
        <div className="who">
          <span className="avatar" aria-hidden="true">
            {(session.memberName || session.username).charAt(0).toUpperCase()}
          </span>
          <Link to="/settings" className="who-text" title="Your settings">
            <span className="who-name">{session.memberName || session.username}</span>
            <span className="who-role">{isManager ? 'Manager' : 'Member'}</span>
          </Link>
          <button type="button" className="link-button" onClick={logout}>
            Sign out
          </button>
        </div>
      </header>

      {inMonth && (
        <nav className="tabs" aria-label="Sections">
          {tabs.map((tab) => (
            <NavLink key={tab.to} to={`${base}/${tab.to}`}>
              <tab.icon />
              {tab.label}
            </NavLink>
          ))}
        </nav>
      )}

      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
