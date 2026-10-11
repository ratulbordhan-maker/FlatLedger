// Formatting helpers used across the app.

// Bangladesh groups digits like India: 1,00,000.00
const takaFormat = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** 12500 → "৳12,500.00", -200 → "−৳200.00" */
export function formatTaka(value) {
  if (value === null || value === undefined) return '–'
  const number = Number(value)
  return `${number < 0 ? '−' : ''}৳${takaFormat.format(Math.abs(number))}`
}

/** Amount without a sign, for places where the label already says owes or credit. */
export function formatTakaPlain(value) {
  return `৳${takaFormat.format(Math.abs(Number(value)))}`
}

/** The meal rate rounded for display (the exact rate is kept on the server). */
export function formatRate(rate) {
  if (rate === null || rate === undefined) return '–'
  return `৳${Number(rate).toFixed(2)}`
}

/** 60 → "60", 45.5 → "45.5", 30.25 → "30.25" */
export function formatMeals(value) {
  const rounded = Math.round(Number(value) * 100) / 100
  return String(rounded)
}

/** "2026-10-05" style date for the API */
export function isoDate(year, month, day) {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

/** "5 Oct" */
export function shortDate(value) {
  return new Date(value).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
}
