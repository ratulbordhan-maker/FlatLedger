import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { Icons, Thali } from '../components/Art.jsx'
import { BalanceTag, ErrorNote, Loading } from '../components/Feedback.jsx'
import { CancelEntryDialog, ConfirmDialog } from '../components/Modal.jsx'
import { formatMeals, formatRate, formatTaka, formatTakaPlain, shortDate } from '../lib/format.js'
import { useApi } from '../lib/useApi.js'

/**
 * The month at a glance.
 * Everyone sees the meal rate and each member's meals and cost.
 * The manager also sees everyone's balance and where the flat's cash is.
 * A member sees their own account instead.
 */
export default function DashboardPage() {
  const { year, month } = useParams()
  const { isManager } = useAuth()
  const base = `/months/${year}/${month}`
  const pages = `/m/${year}/${month}`

  const summary = useApi(`${base}/meal-summary`)
  const accounts = useApi(isManager ? `${base}/accounts` : null)
  const cash = useApi(isManager ? `${base}/cash` : null)
  const mine = useApi(isManager ? null : `${base}/accounts/me`)

  if (summary.error) {
    return <ErrorNote error={summary.error} notFoundText="This month hasn’t been opened yet." />
  }
  if (!summary.data) return <Loading />

  const s = summary.data

  return (
    <div className="page dashboard">
      <Hero summary={s} />
      <Stats summary={s} cash={isManager ? cash.data : null} />

      {isManager && s.status === 'OPEN' && (
        <GettingStarted summary={s} accounts={accounts.data} cash={cash.data} pages={pages} />
      )}

      <div className={`dashboard-columns ${isManager ? '' : 'single'}`}>
        <div>
          {isManager ? (
            <AccountsCard accounts={accounts} pages={pages} />
          ) : (
            <>
              <MyAccountCard account={mine} />
              <SharedMealsCard summary={s} />
            </>
          )}
        </div>
        {isManager && <CashCard cash={cash} pages={pages} />}
      </div>

      {isManager && (
        <MonthControls
          year={Number(year)}
          month={Number(month)}
          summary={s}
          onChanged={() => {
            summary.reload()
            accounts.reload()
            cash.reload()
          }}
        />
      )}
    </div>
  )
}

/**
 * Manager: the month's life cycle.
 * Close freezes the figures; Reopen (with a reason) unlocks them; and from
 * the latest month you can open the next one.
 */
function MonthControls({ year, month, summary, onChanged }) {
  const navigate = useNavigate()
  const months = useApi('/months')
  const [dialog, setDialog] = useState(null) // 'close' | 'reopen' | 'next'

  const isLatest = months.data?.[0]?.year === year && months.data?.[0]?.month === month
  const next = new Date(year, month, 1) // JavaScript months start at 0, so this is the following month
  const nextYear = next.getFullYear()
  const nextMonth = next.getMonth() + 1
  const nextLabel = next.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })
  const open = summary.status === 'OPEN'

  const changed = () => {
    window.dispatchEvent(new Event('flatmeals:months-changed'))
    months.reload()
    onChanged()
  }

  return (
    <section className="card month-controls">
      <div>
        <h2>{summary.label}</h2>
        <p className="muted">
          {open
            ? 'Open. At the end of the month, close it to freeze the meal rate and everyone’s balances.'
            : 'Closed. Its figures are final and nothing in it can be changed.'}
        </p>
      </div>
      <div className="action-row">
        <Link className="button quiet" to={`/m/${year}/${month}/report`}>
          Month report
        </Link>
        {open ? (
          <button type="button" className="button quiet" onClick={() => setDialog('close')}>
            Close {summary.label}
          </button>
        ) : (
          <button type="button" className="button quiet" onClick={() => setDialog('reopen')}>
            Reopen
          </button>
        )}
        {isLatest && (
          <button type="button" className="button primary" onClick={() => setDialog('next')}>
            Open {nextLabel}
          </button>
        )}
      </div>

      <ConfirmDialog
        open={dialog === 'close'}
        title={`Close ${summary.label}?`}
        confirmLabel={`Close ${summary.label}`}
        busyLabel="Closing…"
        onClose={() => setDialog(null)}
        onConfirm={async () => {
          await api(`/months/${year}/${month}/close`, { method: 'POST' })
          setDialog(null)
          changed()
        }}
      >
        <p>
          The meal rate ({formatRate(summary.mealRate)}), {formatMeals(summary.totalMeals)} meals and everyone’s balances are
          saved as final.
        </p>
        <p>Nothing in {summary.label} can be changed afterwards unless you reopen it. Balances carry into the next month.</p>
      </ConfirmDialog>

      <CancelEntryDialog
        open={dialog === 'reopen'}
        title={`Reopen ${summary.label}?`}
        summary={`${summary.label} becomes editable again. Its frozen figures are kept in the history.`}
        note="Only the latest closed month can be reopened. The reason is saved for everyone to see."
        confirmLabel="Reopen month"
        busyLabel="Reopening…"
        keepLabel="Keep it closed"
        danger={false}
        onClose={() => setDialog(null)}
        onConfirm={async (reason) => {
          await api(`/months/${year}/${month}/reopen`, { method: 'POST', body: { reason } })
          setDialog(null)
          changed()
        }}
      />

      <ConfirmDialog
        open={dialog === 'next'}
        title={`Open ${nextLabel}?`}
        confirmLabel={`Open ${nextLabel}`}
        busyLabel="Opening…"
        onClose={() => setDialog(null)}
        onConfirm={async () => {
          await api('/months', { method: 'POST', body: { year: nextYear, month: nextMonth } })
          setDialog(null)
          window.dispatchEvent(new Event('flatmeals:months-changed'))
          navigate(`/m/${nextYear}/${nextMonth}/dashboard`)
        }}
      >
        <p>Everyone who lives in the flat now is added to {nextLabel}.</p>
        <p>{summary.label}’s balances and cash carry over automatically. You can keep both months open while you finish {summary.label}.</p>
      </ConfirmDialog>
    </section>
  )
}

/** Green banner with one plain sentence that explains the month. */
function Hero({ summary }) {
  const hasRate = summary.mealRate !== null && summary.mealRate !== undefined
  return (
    <section className="hero" aria-label="This month">
      <div>
        <p className="hero-month">
          {summary.label}
          <span className="month-status">{summary.status === 'OPEN' ? 'Open' : 'Closed'}</span>
        </p>
        {hasRate ? (
          <p className="sentence">
            <strong>{formatMeals(summary.totalMeals)} meals</strong> cost <strong>{formatTaka(summary.totalExpense)}</strong>,
            so one meal costs{' '}
            <strong className="rate" title={`Exact rate ৳${summary.mealRate}`}>{formatRate(summary.mealRate)}</strong>.
          </p>
        ) : (
          <p className="sentence">No meals recorded yet.</p>
        )}
        <p className="hero-note">
          {summary.members.length} {summary.members.length === 1 ? 'member' : 'members'} this month.
          {!hasRate
            ? ' The meal rate appears once meals and groceries are in.'
            : summary.status === 'OPEN'
              ? ' Figures update as you add meals and expenses.'
              : ' This month is closed, so its figures are final.'}
        </p>
      </div>
      <Thali className="hero-art" />
    </section>
  )
}

/** Four key figures. Cash is shown to the manager only. */
function Stats({ summary, cash }) {
  const items = [
    { label: 'Meal rate', value: formatRate(summary.mealRate), icon: Icons.meals, tone: 'green' },
    { label: 'Meals eaten', value: formatMeals(summary.totalMeals), icon: Icons.overview, tone: 'yellow' },
    { label: 'Spent on food', value: formatTaka(summary.totalExpense), icon: Icons.expenses, tone: 'red' },
  ]
  if (cash) items.push({ label: 'Flat cash', value: formatTaka(cash.totalCash), icon: Icons.cash, tone: 'plum' })

  return (
    <div className="stats">
      {items.map((item) => (
        <div key={item.label} className="card stat">
          <span className={`stat-icon ${item.tone}`}>
            <item.icon />
          </span>
          <span>
            <span className="stat-label">{item.label}</span>
            <span className="stat-value">{item.value}</span>
          </span>
        </div>
      ))}
    </div>
  )
}

/**
 * Shown until the month has its basic records. The steps are in the order
 * they should be done, so they are numbered; each one ticks itself off.
 */
function GettingStarted({ summary, accounts, cash, pages }) {
  const hasOpening = accounts?.accounts.some((a) => Number(a.adjustments) !== 0 || Number(a.openingBalance) !== 0)
  const steps = [
    {
      done: hasOpening,
      title: 'Copy over starting balances',
      text: 'What each person owed or was owed in your old spreadsheet.',
      to: `${pages}/payments`,
      action: 'Add adjustment',
    },
    {
      done: Boolean(cash && cash.holders.length > 0),
      title: 'Record the cash people hold',
      text: 'Any flat money the manager or others already have.',
      to: `${pages}/cash`,
      action: 'Correct cash',
    },
    {
      done: Number(summary.totalExpense) > 0,
      title: 'Add this month’s groceries',
      text: 'Each market trip, and who paid.',
      to: `${pages}/expenses`,
      action: 'Add expense',
    },
    {
      done: Number(summary.totalMeals) > 0,
      title: 'Fill in the meals',
      text: 'Who ate lunch and dinner each day.',
      to: `${pages}/meals`,
      action: 'Open meals',
    },
  ]
  if (steps.every((step) => step.done)) return null

  return (
    <section className="card">
      <div className="card-head">
        <h2>Getting started</h2>
        <span className="muted small">{steps.filter((s) => s.done).length} of {steps.length} done</span>
      </div>
      <ol className="checklist">
        {steps.map((step) => (
          <li key={step.title} className={step.done ? 'is-done' : undefined}>
            <span>
              <span className="check-title">{step.title}</span>
              <span className="muted small">{step.text}</span>
            </span>
            {!step.done && (
              <Link className="button quiet small" to={step.to}>
                {step.action}
              </Link>
            )}
          </li>
        ))}
      </ol>
    </section>
  )
}

/** Manager: every member's meals, cost and balance. */
function AccountsCard({ accounts, pages }) {
  if (accounts.error) return <ErrorNote error={accounts.error} />
  if (!accounts.data) return <Loading what="Loading accounts" />

  const a = accounts.data
  return (
    <section className="card flush">
      <div className="card-head">
        <h2>
          <Icons.overview />
          Members
        </h2>
        <p className="muted small">
          Owed to the flat <strong className="text-owes">{formatTakaPlain(a.totalOwed)}</strong>
          <span className="gap" />
          Credit <strong className="text-credit">{formatTakaPlain(a.totalCredit)}</strong>
        </p>
      </div>
      <table className="ledger">
        <thead>
          <tr>
            <th scope="col">Member</th>
            <th scope="col" className="num">Meals</th>
            <th scope="col" className="num">Meal cost</th>
            <th scope="col" className="num">Brought forward</th>
            <th scope="col" className="num">Paid</th>
            <th scope="col" className="num">Balance</th>
          </tr>
        </thead>
        <tbody>
          {a.accounts.map((row) => (
            <tr key={row.memberId}>
              <th scope="row">
                <Link className="name-link" to={`${pages}/account/${row.memberId}`}>{row.name}</Link>
              </th>
              <td className="num" data-label="Meals">{formatMeals(row.mealCount)}</td>
              <td className="num" data-label="Meal cost">{formatTaka(row.mealCost)}</td>
              <td className="num" data-label="Brought forward">{formatTaka(row.openingBalance)}</td>
              <td className="num" data-label="Paid">{formatTaka(row.payments)}</td>
              <td className="num" data-label="Balance"><BalanceTag value={row.closingBalance} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

/** Manager: where the flat's cash is. Kept apart from balances on purpose. */
function CashCard({ cash, pages }) {
  if (cash.error) return <ErrorNote error={cash.error} />
  if (!cash.data) return <Loading what="Loading cash" />

  const c = cash.data
  const counts = Object.fromEntries(c.reconciliation.map((r) => [r.memberId, r]))
  return (
    <section className="card cash-card">
      <div className="card-head">
        <h2>
          <Icons.cash />
          Flat cash
        </h2>
        <p className="cash-total">{formatTaka(c.totalCash)}</p>
      </div>
      <p className="muted small">Money people hold for the flat. It isn’t anyone’s personal credit.</p>
      {c.holders.length === 0 ? (
        <p className="muted small">
          Nobody is holding flat cash yet. <Link to={`${pages}/cash`}>Record starting cash</Link>
        </p>
      ) : (
        <ul className="cash-list">
          {c.holders.map((h) => {
            const check = counts[h.memberId]
            return (
              <li key={h.memberId}>
                <span>{h.name}</span>
                <span className="num text-cash">{formatTaka(h.closingCash)}</span>
                {check && check.status !== 'NOT_COUNTED' && (
                  <span className={`count-note ${check.status === 'MATCHED' ? '' : 'is-off'}`}>
                    {check.status === 'MATCHED'
                      ? 'Count matches'
                      : `Counted ${formatTaka(check.counted)} (${check.status === 'SHORT' ? 'short' : 'over'} by ${formatTakaPlain(check.difference)})`}
                  </span>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

/** Member: their own account for the month. */
function MyAccountCard({ account }) {
  if (account.error?.status === 404) {
    return (
      <section className="card">
        <h2>Your account</h2>
        <p className="muted">Nothing is recorded for you this month yet.</p>
      </section>
    )
  }
  if (account.error) return <ErrorNote error={account.error} />
  if (!account.data) return <Loading what="Loading your account" />

  const me = account.data
  const recent = [...me.transactions].reverse().slice(0, 6)
  return (
    <section className="card">
      <div className="card-head">
        <h2>Your account</h2>
        <BalanceTag value={me.closingBalance} />
      </div>
      <dl className="figures">
        <div><dt>Brought forward</dt><dd>{formatTaka(me.openingBalance)}</dd></div>
        <div><dt>Meals</dt><dd>{formatMeals(me.mealCount)}</dd></div>
        <div><dt>Meal cost</dt><dd>{formatTaka(me.mealCost)}</dd></div>
        <div><dt>Paid</dt><dd>{formatTaka(me.payments)}</dd></div>
        {Number(me.pocketCredit) !== 0 && <div><dt>Bought for the flat</dt><dd>{formatTaka(me.pocketCredit)}</dd></div>}
        {Number(me.adjustments) !== 0 && <div><dt>Adjustments</dt><dd>{formatTaka(me.adjustments)}</dd></div>}
      </dl>
      {recent.length > 0 && (
        <>
          <h3>Recent</h3>
          <ul className="history">
            {recent.map((t, i) => (
              <li key={i} className={t.voided ? 'is-voided' : ''}>
                <span className="history-date">{shortDate(t.date)}</span>
                <span className="history-text">
                  {t.description}
                  {t.voided && <span className="muted small"> (cancelled: {t.voidReason})</span>}
                </span>
                <span className="num">{formatTaka(t.amount)}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}

/** Member: everyone's meals and costs (shared information). */
function SharedMealsCard({ summary }) {
  return (
    <section className="card flush">
      <div className="card-head">
        <h2>Everyone’s meals</h2>
      </div>
      <table className="ledger keep-table">
        <thead>
          <tr>
            <th scope="col">Member</th>
            <th scope="col" className="num">Meals</th>
            <th scope="col" className="num">Meal cost</th>
          </tr>
        </thead>
        <tbody>
          {summary.members.map((m) => (
            <tr key={m.memberId}>
              <th scope="row">{m.name}</th>
              <td className="num">{formatMeals(m.meals)}</td>
              <td className="num">{formatTaka(m.cost)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
