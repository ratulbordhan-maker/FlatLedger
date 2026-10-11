// Helpers shared by all the money forms.
import { useEffect, useState } from 'react'
import { isoDate } from './format.js'

/** First and last date of a month, for <input type="date" min max>. */
export function monthRange(year, month) {
  const last = new Date(Number(year), Number(month), 0).getDate()
  return { min: isoDate(year, month, 1), max: isoDate(year, month, last) }
}

/** Today if it falls in the month, otherwise the 1st. */
export function defaultDate(year, month) {
  const now = new Date()
  if (now.getFullYear() === Number(year) && now.getMonth() + 1 === Number(month)) {
    return isoDate(year, month, now.getDate())
  }
  return isoDate(year, month, 1)
}

export function dateProblem(value, year, month) {
  if (!value) return 'Choose a date.'
  const { min, max } = monthRange(year, month)
  if (value < min || value > max) return 'The date must be in this month.'
  return null
}

/**
 * Checks an amount typed by the user.
 * Accepts 9000, 9000.5, 9000.50. Rejects letters, commas and 3 decimals.
 */
export function moneyProblem(text, { allowZero = false } = {}) {
  const value = String(text ?? '').trim()
  if (!value) return 'Enter an amount.'
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(value)) return 'Use numbers only, with up to 2 decimals (for example 450.50).'
  if (!allowZero && Number(value) === 0) return 'The amount must be more than 0.'
  return null
}

export function requiredProblem(value, message) {
  return String(value ?? '').trim() ? null : message
}

/** Removes the keys whose value is null, leaving only real problems. */
export function onlyProblems(problems) {
  return Object.fromEntries(Object.entries(problems).filter(([, message]) => message))
}

/**
 * Form state that resets every time the form is opened.
 *   const [values, set] = useFormValues({ amount: '' }, open)
 *   <input value={values.amount} onChange={set('amount')} />
 */
export function useFormValues(initial, open) {
  const [values, setValues] = useState(initial)

  useEffect(() => {
    if (open) setValues(initial)
    // Only reset when the form opens, not on every render
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const set = (name) => (eventOrValue) => {
    const target = eventOrValue?.target
    const value = target ? (target.type === 'checkbox' ? target.checked : target.value) : eventOrValue
    setValues((previous) => ({ ...previous, [name]: value }))
  }

  return [values, set, setValues]
}

/** Member name from a members list, with a fallback. */
export function nameOf(members, id) {
  if (id === null || id === undefined || id === '') return '–'
  return members?.find((m) => m.id === Number(id))?.name ?? `Member ${id}`
}
