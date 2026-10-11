import { useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { Coins } from '../components/Art.jsx'
import { BalanceTag, ErrorNote, Loading, PageHead } from '../components/Feedback.jsx'
import { formatMeals, formatRate, formatTaka, formatTakaPlain, shortDate } from '../lib/format.js'
import { useApi } from '../lib/useApi.js'

const TYPE_NAMES = {
  OPENING_BALANCE: 'Brought forward',
  MEAL_COST: 'Meals',
  PAYMENT: 'Payment',
  REFUND: 'Refund',
  ADJUSTMENT: 'Adjustment',
  POCKET_EXPENSE: 'Bought for the flat',
}

/**
 * One member's account for a month, laid out like a statement:
 * what they brought forward, what was added, what was taken off, and the balance.
 * Members see their own; the manager can switch between members.
 */
export default function AccountPage() {
  const { year, month, memberId } = useParams()
  const { isManager, session } = useAuth()
  const navigate = useNavigate()
  const base = `/months/${year}/${month}`
  const viewingOwn = !memberId || Number(memberId) === session.memberId
  const account = useApi(memberId ? `${base}/accounts/${memberId}` : `${base}/accounts/me`)
  const summary = useApi(`${base}/meal-summary`)
  const members = useApi(isManager ? '/members' : null)

  const switcher = isManager && members.data && (
    <label className="member-switch">
      <span className="sr-only">Show the account of</span>
      <select
        value={memberId ?? String(session.memberId ?? '')}
        onChange={(e) => navigate(`/m/${year}/${month}/account/${e.target.value}`)}
      >
        {members.data.map((m) => (
          <option key={m.id} value={m.id}>{m.name}</option>
        ))}
      </select>
    </label>
  )

  if (account.error?.status === 404) {
    return (
      <div className="page">
        <PageHead art={<Coins className="page-art" />} title={viewingOwn ? 'My account' : 'Account'} action={switcher}>
          <p className="muted">Nothing is recorded for this person in this month yet.</p>
        </PageHead>
      </div>
    )
  }
  if (account.error) return <ErrorNote error={account.error} />
  if (!account.data) return <Loading what="Loading the account" />

  const a = account.data
  const monthLabel = summary.data?.label ?? ''
  const rate = summary.data?.mealRate
  const lines = [
    { label: 'Brought forward from last month', value: a.openingBalance, sign: '' },
    { label: `Meals: ${formatMeals(a.mealCount)} × ${rate ? formatRate(rate) : 'meal rate'}`, value: a.mealCost, sign: '+' },
    Number(a.adjustments) !== 0 && { label: 'Adjustments', value: a.adjustments, sign: Number(a.adjustments) < 0 ? '−' : '+' },
    Number(a.refunds) !== 0 && { label: 'Refunds paid back to you', value: a.refunds, sign: '+' },
    { label: 'Payments', value: a.payments, sign: '−' },
    Number(a.pocketCredit) !== 0 && { label: 'Bought for the flat with own money', value: a.pocketCredit, sign: '−' },
  ].filter(Boolean)

  return (
    <div className="page account-page">
      <PageHead
        art={<Coins className="page-art" />}
        title={viewingOwn ? 'My account' : a.name}
        action={
          <span className="action-row">
            {switcher}
            <button type="button" className="button quiet" onClick={() => window.print()}>
              Print
            </button>
          </span>
        }
      >
        <p className="muted">
          {viewingOwn && !isManager ? `${a.name}, ` : ''}
          {monthLabel}
        </p>
      </PageHead>

      <div className="account-columns">
        <section className="card statement" aria-label="Balance worked out">
          <h2>How the balance is worked out</h2>
          <dl>
            {lines.map((line) => (
              <div key={line.label} className="statement-line">
                <dt>{line.label}</dt>
                <dd className="num">
                  {line.sign && <span className="sign">{line.sign}</span>}
                  {line.sign ? formatTakaPlain(line.value) : formatTaka(line.value)}
                </dd>
              </div>
            ))}
            <div className="statement-line statement-total">
              <dt>Balance</dt>
              <dd className="num">
                <BalanceTag value={a.closingBalance} />
              </dd>
            </div>
          </dl>
          <p className="muted small">
            {Number(a.closingBalance) > 0
              ? `${viewingOwn ? 'You owe' : `${a.name} owes`} the flat ${formatTakaPlain(a.closingBalance)}.`
              : Number(a.closingBalance) < 0
                ? `The flat owes ${viewingOwn ? 'you' : a.name} ${formatTakaPlain(a.closingBalance)}. It carries into next month as credit.`
                : 'Nothing is owed either way.'}
          </p>
        </section>

        <section className="card flush history-card">
          <div className="card-head">
            <h2>Everything this month</h2>
          </div>
          {a.transactions.length === 0 ? (
            <p className="empty-line">No entries yet.</p>
          ) : (
            <table className="ledger records keep-table">
              <thead>
                <tr>
                  <th scope="col">Date</th>
                  <th scope="col">What</th>
                  <th scope="col" className="num">Effect on balance</th>
                </tr>
              </thead>
              <tbody>
                {a.transactions.map((t, i) => {
                  const amount = Number(t.amount)
                  return (
                    <tr key={i} className={t.voided ? 'is-voided' : undefined}>
                      <td data-label="Date">{shortDate(t.date)}</td>
                      <th scope="row">
                        <span className="record-title">{TYPE_NAMES[t.type] ?? t.type}</span>
                        <span className="record-note">{t.description}</span>
                        {t.voided && <span className="record-note cancel-note">Cancelled: {t.voidReason}</span>}
                      </th>
                      <td className="num amount" data-label="Effect">
                        <span className={t.voided ? '' : amount > 0 ? 'text-owes' : amount < 0 ? 'text-credit' : ''}>
                          {amount > 0 ? '+' : amount < 0 ? '−' : ''}
                          {formatTakaPlain(amount)}
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
          <p className="muted small legend-note">
            <span className="text-owes">+</span> adds to what is owed. <span className="text-credit">−</span> takes it off.
          </p>
        </section>
      </div>
    </div>
  )
}
