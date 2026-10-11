import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, clearSession, readSession, saveSession } from '../api/client.js'

const AuthContext = createContext(null)

/**
 * Keeps track of who is logged in.
 * The session (token, role, member id, name) is saved in the browser so a
 * page refresh doesn't log you out. It expires when the token does.
 *
 * Note: hiding manager buttons here is only for convenience. The backend
 * checks every request, so a member can't do manager actions anyway.
 */
export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => readSession())

  const logout = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  // The API client fires this event when the server says the token is no longer valid
  useEffect(() => {
    window.addEventListener('flatmeals:signed-out', logout)
    return () => window.removeEventListener('flatmeals:signed-out', logout)
  }, [logout])

  const login = useCallback(async (username, password) => {
    clearSession()
    const result = await api('/auth/login', { method: 'POST', body: { username, password } })
    saveSession(result)
    setSession(result)
    return result
  }, [])

  const value = useMemo(
    () => ({ session, isManager: session?.role === 'MANAGER', login, logout }),
    [session, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext)
}
