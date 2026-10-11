import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { api } from '../api/client.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { Thali } from '../components/Art.jsx'
import { ErrorNote, Loading } from '../components/Feedback.jsx'
import { useApi } from '../lib/useApi.js'

/**
 * The address "/" jumps to the current month (or the latest one).
 * If no month exists yet, the manager can open the first one here.
 */
export default function HomePage() {
  const { isManager } = useAuth()
  const navigate = useNavigate()
  const months = useApi('/months')
  const [opening, setOpening] = useState(false)
  const [error, setError] = useState(null)

  if (months.loading) return <Loading />
  if (months.error) return <ErrorNote error={months.error} />

  const now = new Date()
  const thisYear = now.getFullYear()
  const thisMonth = now.getMonth() + 1

  if (months.data.length > 0) {
    const target = months.data.find((m) => m.year === thisYear && m.month === thisMonth) || months.data[0]
    return <Navigate to={`/m/${target.year}/${target.month}/dashboard`} replace />
  }

  const thisMonthLabel = now.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })

  async function openFirstMonth() {
    setOpening(true)
    setError(null)
    try {
      await api('/months', { method: 'POST', body: { year: thisYear, month: thisMonth } })
      navigate(`/m/${thisYear}/${thisMonth}/dashboard`)
    } catch (err) {
      setError(err)
      setOpening(false)
    }
  }

  return (
    <section className="empty-state">
      <Thali className="empty-art" />
      <h1>No months yet</h1>
      {isManager ? (
        <>
          <p>Open the first month to start recording meals. Every active member is added to it.</p>
          {error && <ErrorNote error={error} />}
          <button type="button" className="button primary" onClick={openFirstMonth} disabled={opening}>
            {opening ? 'Opening…' : `Open ${thisMonthLabel}`}
          </button>
        </>
      ) : (
        <p>The manager hasn’t opened a month yet. Check back once they have.</p>
      )}
    </section>
  )
}
