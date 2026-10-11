import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { download } from '../api/client.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { Basket } from '../components/Art.jsx'
import { BalanceTag, ErrorNote, Loading, PageHead } from '../components/Feedback.jsx'
import { formatMeals, formatRate, formatTaka, shortDate } from '../lib/format.js'
import { useApi } from '../lib/useApi.js'

const ADJUSTMENT_NAMES = { OPENING: 'Opening balance', CORRECTION: 'Correction', OTHER: 'Other' }
const CHECK_NAMES = { MATCHED: 'Matches', SHORT: 'Short', OVER: 'Over', NOT_COUNTED: 'Not counted' }

/**
 * The whole month on one page, laid out to print on A4 or save as PDF,
 * with a button to download the same figures as a spreadsheet (CSV).
 * Manager only: it shows everyone's dues. The backend checks this too.
 */
export default function ReportPage() {
  const { year, month } = useParams()
  const { isManager } = useAuth()
  const base = `/months/${year}/${month}`
  const accounts = useApi(isManager ? `${base}/accounts` : null)
  const cash = useApi(isManager ? `${base}/cash` : null)
  const expenses = useApi(isManager ? `${base}/expenses` : null)
  const payments = useApi(isManager ? `${base}/payments` : null)
  const adjustments = useApi(isManager ? `${base}/adjustments` : null)
  const members = useApi(isManager ? '/members' : null)
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState(null)

  if (!isManager) {
    return (
      <div className="page">
        <ErrorNote
          error={{ message: 'Only the manager can see the month report. Your own figures are on My account.' }}
        />
        <Link to={`/m/${year}/${month}/account`}>Go to My account</Link>
      </div>
    )
  }

  const failed = [accounts, cash, expenses, payments, adjustments, members].find((r) => r.error)
  if (failed) return <ErrorNote error={failed.error} notFoundText="This month hasn’t been opened yet." />
  if (!accounts.data || !cash.data || !expenses.data || !payments.data || !adjustments.data || !members.data) {
    return <Loading what="Putting the report together" />
  }

  const a = accounts.data
  const names = Object.fromEntries(members.data.map((m) => [m.id, m.name]))
  const nameOf = (id) => names[id] ?? `Member ${id}`
  const activeExpenses = expenses.data.filter((e) => e.status === 'ACTIVE')
  const expenseTotal = activeExpenses.reduce((sum, e) => sum + Number(e.amount), 0)
  const mealExpenseTotal = activeExpenses.filter((e) => e.inMealRate).reduce((sum, e) => sum + Number(e.amount), 0)
  const fileName = `flat-meals-${year}-${String(month).padStart(2, '0')}.csv`

  async function downloadCsv() {
    setDownloading(true)
    setDownloadError(null)
    try {
      await download(`${base}/report.csv`, fileName)
    } catch (err) {
      setDownloadError(err)
    } finally {
      setDownloading(false)
    }
  }

  return (
    <div className="page report-page">
      <PageHead
        art={<Basket className="page-art" />}
        title={`${a.label} report`}
        action={
          <span className="action-row">
            <button type="button" className="button quiet" onClick={() => window.print()}>
              Print or save as PDF
            </button>
            <button type="button" className="button primary" onClick={downloadCsv} disabled={downloading}>
              {downloading ? 'Downloading…' : 'Download CSV'}
            </button>
          </span>
        }
      >
        <p className="muted">
          {a.status === 'CLOSED'
            ? 'Closed: these figures are final.'
            : 'Open: figures can still change until the month is closed.'}
        </p>
      </PageHead>
      {downloadError && <ErrorNote error={downloadError} />}

      <section className="card report-summary" aria-label="Month in numbers">
        <dl>
          <div>
            <dt>Total meals</dt>
            <dd>{formatMeals(a.totalMeals)}</dd>
          </div>
          <div>
            <dt>Meal expense</dt>
            <dd>{formatTaka(a.totalExpense)}</dd>
          </div>
          <div>
            <dt>Meal rate</dt>
            <dd>
              {formatRate(a.mealRate)} <span className="muted small">a meal</span>
            </dd>
          </div>
          <div>
            <dt>Members owe</dt>
            <dd className="text-owes">{formatTaka(a.totalOwed)}</dd>
          </div>
          <div>
            <dt>Credit held</dt>
            <dd className="text-credit">{formatTaka(a.totalCredit)}</dd>
          </div>
          <div>
            <dt>Flat cash</dt>
            <dd className="text-cash">{formatTaka(cash.data.totalCash)}</dd>
          </div>
        </dl>
        {a.warning && <p className="warning-note">{a.warning}</p>}
      </section>

      <ReportTable title="Members" empty="No members in this month." count={a.accounts.length}>
        <thead>
          <tr>
            <th scope="col">Member</th>
            <th scope="col" className="num">
              Brought forward
            </th>
            <th scope="col" className="num">
              Meals
            </th>
            <th scope="col" className="num">
              Meal cost
            </th>
            <th scope="col" className="num">
              Paid
            </th>
            <th scope="col" className="num">
              Other
            </th>
            <th scope="col" className="num">
              Balance
            </th>
          </tr>
        </thead>
        <tbody>
          {a.accounts.map((m) => {
            const other = Number(m.adjustments) + Number(m.refunds) - Number(m.pocketCredit)
            return (
              <tr key={m.memberId}>
                <th scope="row">{m.name}</th>
                <td className="num">{formatTaka(m.openingBalance)}</td>
                <td className="num">{formatMeals(m.mealCount)}</td>
                <td className="num">{formatTaka(m.mealCost)}</td>
                <td className="num">{formatTaka(m.payments)}</td>
                <td className="num">{formatTaka(other)}</td>
                <td className="num">
                  <BalanceTag value={m.closingBalance} />
                </td>
              </tr>
            )
          })}
        </tbody>
      </ReportTable>
      <p className="muted small report-footnote">
        Other = adjustments and refunds, minus anything bought for the flat with their own money.
      </p>

      <ReportTable title="Expenses" empty="No expenses recorded." count={expenses.data.length}>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">What</th>
            <th scope="col">Paid from</th>
            <th scope="col" className="num">
              Amount
            </th>
          </tr>
        </thead>
        <tbody>
          {expenses.data.map((e) => (
            <tr key={e.id} className={e.status === 'VOIDED' ? 'is-voided' : undefined}>
              <td>{shortDate(e.expenseDate)}</td>
              <th scope="row">
                <span className="record-title">{e.category}</span>
                {e.description && <span className="record-note">{e.description}</span>}
                {!e.inMealRate && <span className="record-note">Not in the meal rate</span>}
                {e.status === 'VOIDED' && <span className="record-note cancel-note">Cancelled: {e.voidReason}</span>}
              </th>
              <td>
                {e.fundedBy === 'MEMBER_POCKET'
                  ? `${nameOf(e.holderMemberId)}’s own money`
                  : `Flat cash, ${nameOf(e.holderMemberId)}`}
              </td>
              <td className="num amount">{formatTaka(e.amount)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row" colSpan={3}>
              Total (meal rate: {formatTaka(mealExpenseTotal)})
            </th>
            <td className="num">{formatTaka(expenseTotal)}</td>
          </tr>
        </tfoot>
      </ReportTable>

      <ReportTable title="Payments and refunds" empty="No payments recorded." count={payments.data.length}>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Member</th>
            <th scope="col">Cash handled by</th>
            <th scope="col" className="num">
              Amount
            </th>
          </tr>
        </thead>
        <tbody>
          {payments.data.map((p) => (
            <tr key={p.id} className={p.status === 'VOIDED' ? 'is-voided' : undefined}>
              <td>{shortDate(p.paidOn)}</td>
              <th scope="row">
                <span className="record-title">
                  {nameOf(p.memberId)}
                  {p.type === 'REFUND' ? ' (refund)' : ''}
                </span>
                {p.note && <span className="record-note">{p.note}</span>}
                {p.status === 'VOIDED' && <span className="record-note cancel-note">Cancelled: {p.voidReason}</span>}
              </th>
              <td>{nameOf(p.cashHolderId)}</td>
              <td className="num amount">{formatTaka(p.amount)}</td>
            </tr>
          ))}
        </tbody>
      </ReportTable>

      {adjustments.data.length > 0 && (
        <ReportTable title="Adjustments" count={adjustments.data.length}>
          <thead>
            <tr>
              <th scope="col">Member</th>
              <th scope="col">Reason</th>
              <th scope="col" className="num">
                Effect on balance
              </th>
            </tr>
          </thead>
          <tbody>
            {adjustments.data.map((adj) => (
              <tr key={adj.id} className={adj.status === 'VOIDED' ? 'is-voided' : undefined}>
                <th scope="row">{nameOf(adj.memberId)}</th>
                <td>
                  <span className="record-title">{ADJUSTMENT_NAMES[adj.type] ?? adj.type}</span>
                  <span className="record-note">{adj.reason}</span>
                  {adj.status === 'VOIDED' && (
                    <span className="record-note cancel-note">Cancelled: {adj.voidReason}</span>
                  )}
                </td>
                <td className="num amount">{formatTaka(adj.amount)}</td>
              </tr>
            ))}
          </tbody>
        </ReportTable>
      )}

      <ReportTable title="Flat cash" empty="Nobody is holding flat cash." count={cash.data.holders.length}>
        <thead>
          <tr>
            <th scope="col">Held by</th>
            <th scope="col" className="num">
              At start
            </th>
            <th scope="col" className="num">
              In
            </th>
            <th scope="col" className="num">
              Out
            </th>
            <th scope="col" className="num">
              Now
            </th>
            <th scope="col">Last count</th>
          </tr>
        </thead>
        <tbody>
          {cash.data.holders.map((h) => {
            const check = cash.data.reconciliation.find((r) => r.memberId === h.memberId)
            return (
              <tr key={h.memberId}>
                <th scope="row">{h.name}</th>
                <td className="num">{formatTaka(h.openingCash)}</td>
                <td className="num">{formatTaka(h.cashIn)}</td>
                <td className="num">{formatTaka(h.cashOut)}</td>
                <td className="num">{formatTaka(h.closingCash)}</td>
                <td>
                  {check ? CHECK_NAMES[check.status] : 'Not counted'}
                  {check &&
                    check.difference !== null &&
                    Number(check.difference) !== 0 &&
                    ` (${formatTaka(check.difference)})`}
                </td>
              </tr>
            )
          })}
        </tbody>
      </ReportTable>
    </div>
  )
}

/** A titled card holding one table; scrolls sideways on narrow phones. */
function ReportTable({ title, children, empty, count }) {
  const isEmpty = count === 0
  return (
    <section className="card flush report-section">
      <div className="card-head">
        <h2>{title}</h2>
      </div>
      {isEmpty ? (
        <p className="empty-line">{empty}</p>
      ) : (
        <div className="table-scroll">
          <table className="ledger keep-table report-table">{children}</table>
        </div>
      )}
    </section>
  )
}
