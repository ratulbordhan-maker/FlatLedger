import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { useApi } from './useApi.js'

/**
 * What every month page needs: the month (is it open?), the member list
 * for dropdowns, whether the user may change things, and a short-lived
 * "Saved." message.
 */
export function useMonthPage() {
  const { year, month } = useParams()
  const { isManager } = useAuth()
  const base = `/months/${year}/${month}`
  const monthInfo = useApi(base)
  const members = useApi('/members')
  const [flash, setFlash] = useState(null)

  useEffect(() => {
    if (!flash) return undefined
    const timer = setTimeout(() => setFlash(null), 4000)
    return () => clearTimeout(timer)
  }, [flash])

  const status = monthInfo.data?.month?.status
  return {
    year: Number(year),
    month: Number(month),
    base,
    monthInfo,
    members: members.data || [],
    reloadMembers: members.reload,
    isManager,
    isOpen: status === 'OPEN',
    canChange: isManager && status === 'OPEN',
    label: monthInfo.data?.month?.label,
    flash,
    setFlash,
  }
}
