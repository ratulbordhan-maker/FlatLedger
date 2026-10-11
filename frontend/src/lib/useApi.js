import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client.js'

/**
 * Loads data from the backend when a page opens, and again whenever the path changes.
 *
 *   const meals = useApi('/months/2026/10/meals')
 *   meals.loading, meals.data, meals.error, meals.reload(), meals.setData(newData)
 *
 * Pass null as the path to skip loading (e.g. data only managers can see).
 */
export function useApi(path) {
  const [state, setState] = useState({ data: null, error: null, loading: Boolean(path) })
  const [version, setVersion] = useState(0)

  useEffect(() => {
    if (!path) {
      setState({ data: null, error: null, loading: false })
      return
    }
    let cancelled = false
    setState((previous) => ({ ...previous, loading: true, error: null }))
    api(path)
      .then((data) => {
        if (!cancelled) setState({ data, error: null, loading: false })
      })
      .catch((error) => {
        if (!cancelled) setState({ data: null, error, loading: false })
      })
    // If the page changes before the answer arrives, ignore the old answer
    return () => {
      cancelled = true
    }
  }, [path, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  const setData = useCallback((data) => setState((previous) => ({ ...previous, data })), [])

  return { ...state, reload, setData }
}
