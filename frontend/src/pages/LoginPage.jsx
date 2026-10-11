import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { LeafMark, Thali } from '../components/Art.jsx'

export default function LoginPage() {
  const { session, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  if (session) return <Navigate to="/" replace />

  async function handleSubmit(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username.trim(), password)
      navigate(location.state?.from?.pathname || '/', { replace: true })
    } catch (err) {
      setError(err.status === 401 ? 'That username and password don’t match. Check them and try again.' : err.message)
      setBusy(false)
    }
  }

  return (
    <main className="login">
      <section className="login-side" aria-hidden="true">
        <Thali className="thali" />
        <p className="login-tagline">Meals, money and the flat’s cash, worked out for everyone.</p>
      </section>

      <div className="login-main">
        <form className="login-form" onSubmit={handleSubmit}>
          <h1 className="login-title">
            <LeafMark className="brand-mark" />
            Flat Meals
          </h1>
          <p className="muted">Sign in to see this month’s meals and your account.</p>

          <div className="field">
            <label htmlFor="login-username">Username</label>
            <input
              id="login-username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              required
            />
          </div>

          <div className="field">
            <label htmlFor="login-password">Password</label>
            <input
              id="login-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </div>

          {error && (
            <p className="error-note" role="alert">
              {error}
            </p>
          )}

          <button type="submit" className="button primary wide" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </main>
  )
}
