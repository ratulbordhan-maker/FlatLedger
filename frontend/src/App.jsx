import { useEffect, useState } from 'react'

// Phase 1 smoke test: calls the backend health endpoint and shows the result.
export default function App() {
  const [health, setHealth] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetch('/api/health')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then(setHealth)
      .catch((err) => setError(err.message))
  }, [])

  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', padding: 24, maxWidth: 480 }}>
      <h1>Flat Meal Manager</h1>
      {health && <p style={{ color: 'green' }}>✅ Backend status: {health.status} · database schema version {health.schemaVersion}</p>}
      {error && <p style={{ color: 'crimson' }}>❌ Could not reach the backend ({error}). Is Spring Boot running on port 8080?</p>}
      {!health && !error && <p>Checking backend…</p>}
    </main>
  )
}