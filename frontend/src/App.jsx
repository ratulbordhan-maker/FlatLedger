import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth/AuthContext.jsx'
import Layout from './components/Layout.jsx'
import LoginPage from './pages/LoginPage.jsx'
import HomePage from './pages/HomePage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import MealsPage from './pages/MealsPage.jsx'
import ExpensesPage from './pages/ExpensesPage.jsx'
import PaymentsPage from './pages/PaymentsPage.jsx'
import CashPage from './pages/CashPage.jsx'
import MembersPage from './pages/MembersPage.jsx'
import AccountPage from './pages/AccountPage.jsx'
import SettingsPage from './pages/SettingsPage.jsx'
import ReportPage from './pages/ReportPage.jsx'

/** Sends anyone who is not logged in to the login page. */
function RequireAuth({ children }) {
  const { session } = useAuth()
  const location = useLocation()
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return children
}

/**
 * All the pages of the app.
 * Month pages live under /m/:year/:month/..., so every month has its own URL
 * and the browser's back button moves between months.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<HomePage />} />
        <Route path="/m/:year/:month" element={<Navigate to="dashboard" replace />} />
        <Route path="/m/:year/:month/dashboard" element={<DashboardPage />} />
        <Route path="/m/:year/:month/meals" element={<MealsPage />} />
        <Route path="/m/:year/:month/expenses" element={<ExpensesPage />} />
        <Route path="/m/:year/:month/payments" element={<PaymentsPage />} />
        <Route path="/m/:year/:month/cash" element={<CashPage />} />
        <Route path="/m/:year/:month/members" element={<MembersPage />} />
        <Route path="/m/:year/:month/account" element={<AccountPage />} />
        <Route path="/m/:year/:month/account/:memberId" element={<AccountPage />} />
        <Route path="/m/:year/:month/report" element={<ReportPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
