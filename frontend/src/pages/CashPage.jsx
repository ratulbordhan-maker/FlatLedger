import { useState } from 'react'
import { api } from '../api/client.js'
import { ClayPot } from '../components/Art.jsx'
import { EmptyCard, ErrorNote, Loading, PageHead } from '../components/Feedback.jsx'
import { Choice, Field, MemberSelect, MoneyInput } from '../components/Fields.jsx'
import { CancelEntryDialog, FormModal } from '../components/Modal.jsx'
import { formatTaka, formatTakaPlain, shortDate } from '../lib/format.js'
import {
  dateProblem,
  defaultDate,
  monthRange,
  moneyProblem,
  nameOf,
  onlyProblems,
  requiredProblem,
  useFormValues,
} from '../lib/forms.js'
import { useApi } from '../lib/useApi.js'
import { useMonthPage } from '../lib/useMonthPage.js'

const CORRECTION_TYPES = {
  OPENING: 'Cash they already had when we started',
  SHORTAGE: 'Cash found missing after a count',
  EXCESS: 'Extra cash found after a count',
  OTHER: 'Other correction',
}

/**
 * Manager only. Where the flat's physical money is, and whether the
 * records match what people actually have in hand.
 */
export default function CashPage() {
  const page = useMonthPage()
  const report = useApi(`${page.base}/cash`)
  const transfers = useApi(`${page.base}/cash/transfers`)
  const receipts = useApi(`${page.base}/cash/receipts`)
  const corrections = useApi(`${page.base}/cash/adjustments`)
  const [openForm, setOpenForm] = useState(null) // 'transfer' | 'count' | 'receipt' | 'correction'
  const [cancelling, setCancelling] = useState(null) // { kind, record }

  if (report.error) return <ErrorNote error={report.error} notFoundText="This month hasn’t been opened yet." />
  if (!report.data || !transfers.data || !receipts.data || !corrections.data) return <Loading what="Loading cash" />

  const r = report.data
  const reloadAll = () => {
    report.reload()
    transfers.reload()
    receipts.reload()
    corrections.reload()
  }
  const finished = (message) => {
    page.setFlash(message)
    setOpenForm(null)
    reloadAll()
  }
  const countFor = (memberId) => r.reconciliation.find((row) => row.memberId === memberId)
  const holding = r.holders.filter((h) => Number(h.closingCash) !== 0).length
  const heldBy = holding === 0 ? 'nobody yet' : holding === 1 ? '1 person' : `${holding} people`

  return (
    <div className="page">
      <PageHead art={<ClayPot className="page-art" />} title="Cash">
        <p className="muted">
          The flat has <strong className="text-cash">{formatTaka(r.totalCash)}</strong> in cash, held by {heldBy}.
          It’s separate from what anyone owes.
        </p>
      </PageHead>

      {page.flash && <p className="flash" role="status">{page.flash}</p>}
      {page.monthInfo.data && !page.isOpen && <p className="banner">{page.label} is closed, so its cash records can’t be changed.</p>}

      {page.canChange && (
        <div className="action-row card">
          <button type="button" className="button primary" onClick={() => setOpenForm('count')}>Record a cash count</button>
          <button type="button" className="button quiet" onClick={() => setOpenForm('transfer')}>Hand over cash</button>
          <button type="button" className="button quiet" onClick={() => setOpenForm('receipt')}>Other income</button>
          <button type="button" className="button quiet" onClick={() => setOpenForm('correction')}>Correct cash</button>
        </div>
      )}

      {r.holders.length === 0 ? (
        <EmptyCard
          art={<ClayPot className="empty-art" />}
          title="Nobody is holding flat cash yet"
          action={
            page.canChange && (
              <button type="button" className="button primary" onClick={() => setOpenForm('correction')}>
                Record starting cash
              </button>
            )
          }
        >
          If the manager already has some of the flat’s money, record it as starting cash. Payments then add to it.
        </EmptyCard>
      ) : (
        <div className="card flush">
        <div className="card-head">
          <h2>Who holds it</h2>
        </div>
        <table className="ledger">
          <thead>
            <tr>
              <th scope="col">Held by</th>
              <th scope="col" className="num">Start of month</th>
              <th scope="col" className="num">In</th>
              <th scope="col" className="num">Out</th>
              <th scope="col" className="num">Should have now</th>
              <th scope="col">Last count</th>
            </tr>
          </thead>
          <tbody>
            {r.holders.map((h) => {
              const check = countFor(h.memberId)
              return (
                <tr key={h.memberId}>
                  <th scope="row">{h.name}</th>
                  <td className="num" data-label="Start of month">{formatTaka(h.openingCash)}</td>
                  <td className="num" data-label="In">{formatTaka(h.cashIn)}</td>
                  <td className="num" data-label="Out">{formatTaka(h.cashOut)}</td>
                  <td className="num" data-label="Should have now"><strong className="text-cash">{formatTaka(h.closingCash)}</strong></td>
                  <td data-label="Last count"><CountStatus check={check} /></td>
                </tr>
              )
            })}
          </tbody>
        </table>
        </div>
      )}

      <RecordList
        title="Cash handed over"
        empty="No handovers this month."
        rows={transfers.data}
        canChange={page.canChange}
        render={(t) => ({
          date: t.transferredOn,
          title: `${nameOf(page.members, t.fromMemberId)} gave ${nameOf(page.members, t.toMemberId)}`,
          note: t.note,
          amount: formatTaka(t.amount),
        })}
        onCancel={(t) => setCancelling({ kind: 'transfers', record: t, summary: `${nameOf(page.members, t.fromMemberId)} gave ${nameOf(page.members, t.toMemberId)} ${formatTaka(t.amount)} on ${shortDate(t.transferredOn)}.` })}
      />

      <RecordList
        title="Other income"
        empty="No other income this month."
        rows={receipts.data}
        canChange={page.canChange}
        render={(x) => ({
          date: x.receivedOn,
          title: x.source,
          note: `Received by ${nameOf(page.members, x.holderMemberId)}`,
          amount: formatTaka(x.amount),
        })}
        onCancel={(x) => setCancelling({ kind: 'receipts', record: x, summary: `${x.source}, ${formatTaka(x.amount)} on ${shortDate(x.receivedOn)}.` })}
      />

      <RecordList
        title="Cash corrections"
        empty="No corrections this month."
        rows={corrections.data}
        canChange={page.canChange}
        render={(c) => ({
          date: null,
          title: `${nameOf(page.members, c.holderMemberId)}: ${CORRECTION_TYPES[c.type]}`,
          note: c.reason,
          amount: `${Number(c.amount) < 0 ? '−' : '+'}${formatTakaPlain(c.amount)}`,
        })}
        onCancel={(c) => setCancelling({ kind: 'adjustments', record: c, summary: `${nameOf(page.members, c.holderMemberId)}, ${formatTaka(c.amount)}: ${c.reason}` })}
      />

      <CountForm open={openForm === 'count'} page={page} report={r} onClose={() => setOpenForm(null)} onDone={() => finished('Cash count recorded.')} />
      <TransferForm open={openForm === 'transfer'} page={page} onClose={() => setOpenForm(null)} onDone={() => finished('Handover recorded.')} />
      <ReceiptForm open={openForm === 'receipt'} page={page} onClose={() => setOpenForm(null)} onDone={() => finished('Income recorded.')} />
      <CorrectionForm open={openForm === 'correction'} page={page} onClose={() => setOpenForm(null)} onDone={() => finished('Cash corrected.')} />

      <CancelEntryDialog
        open={cancelling !== null}
        title="Cancel this entry?"
        summary={cancelling?.summary}
        onClose={() => setCancelling(null)}
        onConfirm={async (reason) => {
          await api(`${page.base}/cash/${cancelling.kind}/${cancelling.record.id}/void`, { method: 'POST', body: { reason } })
          page.setFlash('Entry cancelled.')
          setCancelling(null)
          reloadAll()
        }}
      />
    </div>
  )
}

function CountStatus({ check }) {
  if (!check || check.status === 'NOT_COUNTED') return <span className="muted">Not counted</span>
  if (check.status === 'MATCHED') return <span className="text-credit">Matches ({formatTaka(check.counted)})</span>
  return (
    <span className="text-error">
      Counted {formatTaka(check.counted)}, {check.status === 'SHORT' ? 'short' : 'over'} by {formatTakaPlain(check.difference)}
    </span>
  )
}

/** A small ruled list of records with an optional Cancel link. */
function RecordList({ title, empty, rows, canChange, render, onCancel }) {
  return (
    <section className="card flush spaced">
      <div className="card-head">
        <h2>{title}</h2>
      </div>
      {rows.length === 0 ? (
        <p className="empty-line">{empty}</p>
      ) : (
        <ul className="record-list">
          {rows.map((row) => {
            const shown = render(row)
            const cancelled = row.status === 'VOIDED'
            return (
              <li key={row.id} className={cancelled ? 'is-voided' : undefined}>
                {shown.date && <span className="record-date">{shortDate(shown.date)}</span>}
                <span className="record-main">
                  <span className="record-title">{shown.title}</span>
                  {shown.note && <span className="record-note">{shown.note}</span>}
                  {cancelled && <span className="record-note cancel-note">Cancelled: {row.voidReason}</span>}
                </span>
                <span className="num amount">{shown.amount}</span>
                {canChange && !cancelled && (
                  <button type="button" className="link-button danger-link" onClick={() => onCancel(row)}>
                    Cancel
                  </button>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

function CountForm({ open, page, report, onClose, onDone }) {
  const [v, set] = useFormValues({ holderMemberId: '', countedAmount: '', note: '' }, open)
  const holder = nameOf(page.members, v.holderMemberId)
  const recorded = report.holders.find((h) => h.memberId === Number(v.holderMemberId))?.closingCash ?? 0
  const difference = Number(v.countedAmount || 0) - Number(recorded)

  return (
    <FormModal
      open={open}
      title="Record a cash count"
      onClose={onClose}
      submitLabel="Record count"
      validate={() =>
        onlyProblems({
          holderMemberId: requiredProblem(v.holderMemberId, 'Choose whose cash you counted.'),
          countedAmount: moneyProblem(v.countedAmount, { allowZero: true }),
        })
      }
      review={() => (
        <>
          <p>{holder} has <strong>{formatTaka(v.countedAmount)}</strong> in hand.</p>
          <p>
            The records say {formatTaka(recorded)}.{' '}
            {Math.abs(difference) < 0.005
              ? 'They match.'
              : `That’s ${difference < 0 ? 'short' : 'over'} by ${formatTakaPlain(difference)}. Nothing is charged to anyone automatically; use “Correct cash” once you know why.`}
          </p>
        </>
      )}
      submit={() =>
        api(`${page.base}/cash/counts`, {
          method: 'POST',
          body: { holderMemberId: Number(v.holderMemberId), countedAmount: v.countedAmount.trim(), note: v.note.trim() || null },
        })
      }
      onDone={onDone}
    >
      {(problems) => (
        <>
          <Field label="Whose cash?" problem={problems.holderMemberId}>
            {(props) => <MemberSelect members={page.members} value={v.holderMemberId} onChange={set('holderMemberId')} {...props} />}
          </Field>
          <Field
            label="Amount counted"
            problem={problems.countedAmount}
            hint={v.holderMemberId ? `The records say ${formatTaka(recorded)}.` : undefined}
          >
            {(props) => <MoneyInput value={v.countedAmount} onChange={set('countedAmount')} {...props} />}
          </Field>
          <Field label="Note (optional)" problem={problems.note}>
            {(props) => <input value={v.note} onChange={set('note')} maxLength={255} {...props} />}
          </Field>
        </>
      )}
    </FormModal>
  )
}

function TransferForm({ open, page, onClose, onDone }) {
  const [v, set] = useFormValues(
    { fromMemberId: '', toMemberId: '', amount: '', transferredOn: defaultDate(page.year, page.month), note: '' },
    open,
  )
  const range = monthRange(page.year, page.month)
  const from = nameOf(page.members, v.fromMemberId)
  const to = nameOf(page.members, v.toMemberId)

  return (
    <FormModal
      open={open}
      title="Hand over cash"
      onClose={onClose}
      submitLabel="Record handover"
      validate={() =>
        onlyProblems({
          fromMemberId: requiredProblem(v.fromMemberId, 'Choose who gave the cash.'),
          toMemberId:
            requiredProblem(v.toMemberId, 'Choose who received it.') ||
            (v.fromMemberId === v.toMemberId ? 'Choose two different people.' : null),
          amount: moneyProblem(v.amount),
          transferredOn: dateProblem(v.transferredOn, page.year, page.month),
        })
      }
      review={() => (
        <>
          <p>{from} gives <strong>{formatTaka(v.amount)}</strong> of flat cash to {to} on {shortDate(v.transferredOn)}.</p>
          <p className="muted small">It isn’t income or spending: the flat’s total cash stays the same.</p>
        </>
      )}
      submit={() =>
        api(`${page.base}/cash/transfers`, {
          method: 'POST',
          body: {
            fromMemberId: Number(v.fromMemberId),
            toMemberId: Number(v.toMemberId),
            amount: v.amount.trim(),
            transferredOn: v.transferredOn,
            note: v.note.trim() || null,
          },
        })
      }
      onDone={onDone}
    >
      {(problems) => (
        <>
          <div className="field-row">
            <Field label="From" problem={problems.fromMemberId}>
              {(props) => <MemberSelect members={page.members} value={v.fromMemberId} onChange={set('fromMemberId')} {...props} />}
            </Field>
            <Field label="To" problem={problems.toMemberId}>
              {(props) => <MemberSelect members={page.members} value={v.toMemberId} onChange={set('toMemberId')} {...props} />}
            </Field>
          </div>
          <div className="field-row">
            <Field label="Amount" problem={problems.amount}>
              {(props) => <MoneyInput value={v.amount} onChange={set('amount')} {...props} />}
            </Field>
            <Field label="Date" problem={problems.transferredOn}>
              {(props) => <input type="date" min={range.min} max={range.max} value={v.transferredOn} onChange={set('transferredOn')} {...props} />}
            </Field>
          </div>
          <Field label="Note (optional)" problem={problems.note}>
            {(props) => <input value={v.note} onChange={set('note')} maxLength={255} {...props} />}
          </Field>
        </>
      )}
    </FormModal>
  )
}

function ReceiptForm({ open, page, onClose, onDone }) {
  const [v, set] = useFormValues(
    { holderMemberId: '', amount: '', source: '', receivedOn: defaultDate(page.year, page.month) },
    open,
  )
  const range = monthRange(page.year, page.month)
  const holder = nameOf(page.members, v.holderMemberId)

  return (
    <FormModal
      open={open}
      title="Record other income"
      onClose={onClose}
      submitLabel="Record income"
      validate={() =>
        onlyProblems({
          source: requiredProblem(v.source, 'Say where the money came from.'),
          holderMemberId: requiredProblem(v.holderMemberId, 'Choose who received it.'),
          amount: moneyProblem(v.amount),
          receivedOn: dateProblem(v.receivedOn, page.year, page.month),
        })
      }
      review={() => (
        <p>
          {holder} received <strong>{formatTaka(v.amount)}</strong> from “{v.source.trim()}” on {shortDate(v.receivedOn)}.
          The flat’s cash goes up; nobody’s balance changes.
        </p>
      )}
      submit={() =>
        api(`${page.base}/cash/receipts`, {
          method: 'POST',
          body: { holderMemberId: Number(v.holderMemberId), amount: v.amount.trim(), source: v.source.trim(), receivedOn: v.receivedOn },
        })
      }
      onDone={onDone}
    >
      {(problems) => (
        <>
          <Field label="Where did it come from?" problem={problems.source} hint="For example: Sold the old gas cylinder.">
            {(props) => <input value={v.source} onChange={set('source')} maxLength={255} {...props} />}
          </Field>
          <Field label="Who received it?" problem={problems.holderMemberId}>
            {(props) => <MemberSelect members={page.members} value={v.holderMemberId} onChange={set('holderMemberId')} {...props} />}
          </Field>
          <div className="field-row">
            <Field label="Amount" problem={problems.amount}>
              {(props) => <MoneyInput value={v.amount} onChange={set('amount')} {...props} />}
            </Field>
            <Field label="Date" problem={problems.receivedOn}>
              {(props) => <input type="date" min={range.min} max={range.max} value={v.receivedOn} onChange={set('receivedOn')} {...props} />}
            </Field>
          </div>
        </>
      )}
    </FormModal>
  )
}

function CorrectionForm({ open, page, onClose, onDone }) {
  const [v, set, setValues] = useFormValues({ holderMemberId: '', type: 'OPENING', direction: 'ADD', amount: '', reason: '' }, open)
  const holder = nameOf(page.members, v.holderMemberId)
  const signed = v.direction === 'REMOVE' ? `-${v.amount.trim()}` : v.amount.trim()

  // Picking a kind sets the usual direction (a shortage removes cash, the others add)
  const chooseType = (event) => {
    const type = event.target.value
    setValues((previous) => ({ ...previous, type, direction: type === 'SHORTAGE' ? 'REMOVE' : 'ADD' }))
  }

  return (
    <FormModal
      open={open}
      title="Correct cash"
      onClose={onClose}
      submitLabel="Save correction"
      validate={() =>
        onlyProblems({
          holderMemberId: requiredProblem(v.holderMemberId, 'Choose whose cash this is.'),
          amount: moneyProblem(v.amount),
          reason: requiredProblem(v.reason, 'Explain the correction.'),
        })
      }
      review={() => (
        <>
          <p>
            {v.direction === 'REMOVE' ? 'Remove' : 'Add'} <strong>{formatTaka(v.amount)}</strong>{' '}
            {v.direction === 'REMOVE' ? 'from' : 'to'} the cash {holder} holds.
          </p>
          <p>{CORRECTION_TYPES[v.type]}: {v.reason.trim()}</p>
          <p className="muted small">Only the cash record changes. Nobody’s balance changes.</p>
        </>
      )}
      submit={() =>
        api(`${page.base}/cash/adjustments`, {
          method: 'POST',
          body: { holderMemberId: Number(v.holderMemberId), amount: signed, type: v.type, reason: v.reason.trim() },
        })
      }
      onDone={onDone}
    >
      {(problems) => (
        <>
          <Field label="Whose cash?" problem={problems.holderMemberId}>
            {(props) => <MemberSelect members={page.members} value={v.holderMemberId} onChange={set('holderMemberId')} {...props} />}
          </Field>
          <Field label="Kind" problem={problems.type}>
            {(props) => (
              <select value={v.type} onChange={chooseType} {...props}>
                {Object.entries(CORRECTION_TYPES).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            )}
          </Field>
          <Choice
            legend="Change"
            name="cashDirection"
            value={v.direction}
            onChange={set('direction')}
            options={[
              { value: 'ADD', label: 'Add cash' },
              { value: 'REMOVE', label: 'Remove cash' },
            ]}
          />
          <Field label="Amount" problem={problems.amount}>
            {(props) => <MoneyInput value={v.amount} onChange={set('amount')} {...props} />}
          </Field>
          <Field label="Reason" problem={problems.reason}>
            {(props) => <input value={v.reason} onChange={set('reason')} maxLength={255} {...props} />}
          </Field>
        </>
      )}
    </FormModal>
  )
}
