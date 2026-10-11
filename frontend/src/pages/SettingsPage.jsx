import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { ErrorNote, PageHead } from '../components/Feedback.jsx'
import { LeafMark } from '../components/Art.jsx'

/** Your own settings: currently, changing your password. */
export default function SettingsPage() {
  const { session, isManager } = useAuth()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [problems, setProblems] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    const found = {}
    if (!current) found.current = 'Enter your current password.'
    if (next.length < 8) found.next = 'Use at least 8 characters.'
    else if (next.length > 72) found.next = 'Use 72 characters or fewer.'
    if (repeat !== next) found.repeat = 'The two new passwords don’t match.'
    setProblems(found)
    setSaved(false)
    if (Object.keys(found).length) return

    setBusy(true)
    setError(null)
    try {
      await api('/auth/change-password', { method: 'POST', body: { currentPassword: current, newPassword: next } })
      setSaved(true)
      setCurrent('')
      setNext('')
      setRepeat('')
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page settings-page">
      <PageHead art={<LeafMark className="page-art settings-art" />} title="Your settings">
        <p className="muted">
          Signed in as <strong className="ink">{session.username}</strong> ({isManager ? 'manager' : 'member'}).{' '}
          <Link to="/">Back to this month</Link>
        </p>
      </PageHead>

      <form className="card narrow-card" onSubmit={handleSubmit} noValidate>
        <h2>Change your password</h2>
        {saved && <p className="flash" role="status">Password changed. Use the new one next time you sign in.</p>}

        <div className="field">
          <label htmlFor="pw-current">Current password</label>
          <input id="pw-current" type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" aria-invalid={Boolean(problems.current)} />
          {problems.current && <span className="field-problem">{problems.current}</span>}
        </div>
        <div className="field">
          <label htmlFor="pw-new">New password</label>
          <input id="pw-new" type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" aria-invalid={Boolean(problems.next)} />
          <span className="hint">At least 8 characters.</span>
          {problems.next && <span className="field-problem">{problems.next}</span>}
        </div>
        <div className="field">
          <label htmlFor="pw-repeat">New password again</label>
          <input id="pw-repeat" type="password" value={repeat} onChange={(e) => setRepeat(e.target.value)} autoComplete="new-password" aria-invalid={Boolean(problems.repeat)} />
          {problems.repeat && <span className="field-problem">{problems.repeat}</span>}
        </div>

        {error && <ErrorNote error={error} />}
        <button type="submit" className="button primary" disabled={busy}>
          {busy ? 'Saving…' : 'Change password'}
        </button>
      </form>
    </div>
  )
}
