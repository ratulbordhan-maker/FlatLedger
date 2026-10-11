import { Link } from 'react-router-dom'

/**
 * ‹ October 2026 ›
 * Moves between opened months and keeps you on the same section.
 * The months list from the API is newest first.
 */
export default function MonthSwitcher({ months, year, month, section }) {
  const index = months.findIndex((m) => m.year === year && m.month === month)
  const current = index >= 0 ? months[index] : null
  const older = index >= 0 ? months[index + 1] : null
  const newer = index > 0 ? months[index - 1] : null

  const label =
    current?.label ||
    new Date(year, month - 1, 1).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })
  const linkTo = (m) => `/m/${m.year}/${m.month}/${section}`

  return (
    <div className="month-switcher">
      {older ? (
        <Link className="month-step" to={linkTo(older)} aria-label={`Previous month, ${older.label}`}>
          ‹
        </Link>
      ) : (
        <span className="month-step is-disabled" aria-hidden="true">‹</span>
      )}

      <div className="month-name">
        <span>{label}</span>
        {current && (
          <span className={`month-status ${current.status === 'OPEN' ? 'is-open' : 'is-closed'}`}>
            {current.status === 'OPEN' ? 'Open' : 'Closed'}
          </span>
        )}
      </div>

      {newer ? (
        <Link className="month-step" to={linkTo(newer)} aria-label={`Next month, ${newer.label}`}>
          ›
        </Link>
      ) : (
        <span className="month-step is-disabled" aria-hidden="true">›</span>
      )}
    </div>
  )
}
