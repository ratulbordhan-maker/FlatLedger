// Small shared pieces: loading text, error messages and balance labels.
import { formatTakaPlain } from '../lib/format.js'

export function Loading({ what = 'Loading' }) {
  return (
    <p className="muted loading" role="status">
      {what}…
    </p>
  )
}

/** Explains what went wrong in plain words. */
export function ErrorNote({ error, notFoundText }) {
  const message =
    error?.status === 404 && notFoundText ? notFoundText : error?.message || 'Something went wrong.'
  return (
    <p className="error-note" role="alert">
      {message}
    </p>
  )
}

/**
 * Owes ৳824.86  |  Credit ৳510.31  |  Settled
 * Positive balance = the member owes the flat. Negative = the member has credit.
 */
export function BalanceTag({ value }) {
  const number = Number(value)
  if (number > 0) return <span className="tag tag-owes">Owes {formatTakaPlain(number)}</span>
  if (number < 0) return <span className="tag tag-credit">Credit {formatTakaPlain(number)}</span>
  return <span className="tag tag-settled">Settled</span>
}

/** Page heading: a small illustration, the title, a summary line and the main action. */
export function PageHead({ art, title, children, action }) {
  return (
    <div className="page-head">
      {art}
      <div className="page-head-text">
        <h1>{title}</h1>
        {children}
      </div>
      {action}
    </div>
  )
}

/** An empty list that says what goes here and offers the first step. */
export function EmptyCard({ art, title, children, action }) {
  return (
    <div className="card empty-card">
      {art}
      <div>
        <h3>{title}</h3>
        <p className="muted">{children}</p>
        {action}
      </div>
    </div>
  )
}
