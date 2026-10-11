import { useState } from 'react'
import { api } from '../api/client.js'
import { Coins } from '../components/Art.jsx'
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

const ADJUSTMENT_TYPES = {
  OPENING: 'Starting balance',
  CORRECTION: 'Correction',
  OTHER: 'Other',
}

/**
 * Manager only. Money members pay in (or get back), and approved changes
 * to someone's balance. Mistakes are cancelled with a reason, never deleted.
 */
export default function PaymentsPage() {
  const page = useMonthPage()
  const payments = useApi(`${page.base}/payments`)
  const adjustments = useApi(`${page.base}/adjustments`)
  const [paymentFormOpen, setPaymentFormOpen] = useState(false)
  const [adjustmentFormOpen, setAdjustmentFormOpen] = useState(false)
  const [cancelling, setCancelling] = useState(null) // { kind: 'payment' | 'adjustment', record }

  if (payments.error) return <ErrorNote error={payments.error} notFoundText="This month hasn’t been opened yet." />
  if (!payments.data || !adjustments.data) return <Loading what="Loading payments" />

  const activePayments = payments.data.filter((p) => p.status === 'ACTIVE')
  const received = activePayments.filter((p) => p.type === 'PAYMENT').reduce((s, p) => s + Number(p.amount), 0)

  return (
    <div className="page">
      <PageHead
        art={<Coins className="page-art" />}
        title="Payments"
        action={
          page.canChange && (
            <button type="button" className="button primary" onClick={() => setPaymentFormOpen(true)}>
              Record payment
            </button>
          )
        }
      >
        <p className="muted">
          Received from members this month <strong className="ink">{formatTaka(received)}</strong>
        </p>
      </PageHead>

      {page.flash && <p className="flash" role="status">{page.flash}</p>}
      {page.monthInfo.data && !page.isOpen && (
        <p className="banner">{page.label} is closed, so its payments can’t be changed.</p>
      )}

      {payments.data.length === 0 ? (
        <EmptyCard
          art={<Coins className="empty-art" />}
          title="No payments yet"
          action={
            page.canChange && (
              <button type="button" className="button primary" onClick={() => setPaymentFormOpen(true)}>
                Record the first payment
              </button>
            )
          }
        >
          When someone pays their share, record it here with who received the cash.
        </EmptyCard>
      ) : (
        <div className="card flush">
        <table className="ledger records">
          <thead>
            <tr>
              <th scope="col">Date</th>
              <th scope="col">Member</th>
              <th scope="col">Cash went to</th>
              <th scope="col" className="num">Amount</th>
              {page.canChange && <th scope="col"><span className="sr-only">Actions</span></th>}
            </tr>
          </thead>
          <tbody>
            {payments.data.map((p) => {
              const cancelled = p.status === 'VOIDED'
              const refund = p.type === 'REFUND'
              return (
                <tr key={p.id} className={cancelled ? 'is-voided' : undefined}>
                  <td data-label="Date">{shortDate(p.paidOn)}</td>
                  <th scope="row">
                    <span className="record-title">
                      {nameOf(page.members, p.memberId)}
                      {refund && <span className="source source-pocket">refund</span>}
                    </span>
                    {p.note && <span className="record-note">{p.note}</span>}
                    {cancelled && <span className="record-note cancel-note">Cancelled: {p.voidReason}</span>}
                  </th>
                  <td data-label={refund ? 'Paid out by' : 'Received by'}>
                    {refund && <span className="desktop-only">Paid out by </span>}
                    {nameOf(page.members, p.cashHolderId)}
                  </td>
                  <td className="num amount" data-label="Amount">{formatTaka(p.amount)}</td>
                  {page.canChange && (
                    <td className="actions">
                      {!cancelled && (
                        <button type="button" className="link-button danger-link" onClick={() => setCancelling({ kind: 'payment', record: p })}>
                          Cancel
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              )
            })}
          </tbody>
        </table>
        </div>
      )}

      <section className="card flush spaced">
        <div className="card-head">
          <div>
            <h2>Balance adjustments</h2>
            <p className="muted small">Starting balances from your old spreadsheet, and corrections the flat agreed on.</p>
          </div>
          {page.canChange && (
            <button type="button" className="button quiet" onClick={() => setAdjustmentFormOpen(true)}>
              Add adjustment
            </button>
          )}
        </div>
        {adjustments.data.length === 0 ? (
          <p className="empty-line">No adjustments this month.</p>
        ) : (
          <table className="ledger records">
            <thead>
              <tr>
                <th scope="col">Member</th>
                <th scope="col">Reason</th>
                <th scope="col" className="num">Change</th>
                {page.canChange && <th scope="col"><span className="sr-only">Actions</span></th>}
              </tr>
            </thead>
            <tbody>
              {adjustments.data.map((a) => {
                const cancelled = a.status === 'VOIDED'
                const credit = Number(a.amount) < 0
                return (
                  <tr key={a.id} className={cancelled ? 'is-voided' : undefined}>
                    <th scope="row">{nameOf(page.members, a.memberId)}</th>
                    <td data-label="Reason">
                      <span className="cell-stack">
                        <span className="record-title">{ADJUSTMENT_TYPES[a.type]}</span>
                        <span className="record-note">{a.reason}</span>
                        {cancelled && <span className="record-note cancel-note">Cancelled: {a.voidReason}</span>}
                      </span>
                    </td>
                    <td className="num amount" data-label="Change">
                      <span className={credit ? 'text-credit' : 'text-owes'}>
                        {credit ? `${formatTakaPlain(a.amount)} credit` : `${formatTakaPlain(a.amount)} owed`}
                      </span>
                    </td>
                    {page.canChange && (
                      <td className="actions">
                        {!cancelled && (
                          <button type="button" className="link-button danger-link" onClick={() => setCancelling({ kind: 'adjustment', record: a })}>
                            Cancel
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </section>

      <PaymentForm
        open={paymentFormOpen}
        page={page}
        onClose={() => setPaymentFormOpen(false)}
        onDone={() => {
          page.setFlash('Payment recorded.')
          setPaymentFormOpen(false)
          payments.reload()
        }}
      />

      <AdjustmentForm
        open={adjustmentFormOpen}
        page={page}
        onClose={() => setAdjustmentFormOpen(false)}
        onDone={() => {
          page.setFlash('Adjustment added.')
          setAdjustmentFormOpen(false)
          adjustments.reload()
        }}
      />

      <CancelEntryDialog
        open={cancelling !== null}
        title={cancelling?.kind === 'payment' ? 'Cancel this payment?' : 'Cancel this adjustment?'}
        summary={
          cancelling &&
          (cancelling.kind === 'payment'
            ? `${nameOf(page.members, cancelling.record.memberId)}, ${formatTaka(cancelling.record.amount)} on ${shortDate(cancelling.record.paidOn)}.`
            : `${nameOf(page.members, cancelling.record.memberId)}, ${formatTaka(cancelling.record.amount)}: ${cancelling.record.reason}`)
        }
        onClose={() => setCancelling(null)}
        onConfirm={async (reason) => {
          const path = cancelling.kind === 'payment' ? 'payments' : 'adjustments'
          await api(`${page.base}/${path}/${cancelling.record.id}/void`, { method: 'POST', body: { reason } })
          page.setFlash(cancelling.kind === 'payment' ? 'Payment cancelled.' : 'Adjustment cancelled.')
          setCancelling(null)
          if (cancelling.kind === 'payment') payments.reload()
          else adjustments.reload()
        }}
      />
    </div>
  )
}

function PaymentForm({ open, page, onClose, onDone }) {
  const [v, set] = useFormValues(
    { type: 'PAYMENT', memberId: '', cashHolderId: '', amount: '', paidOn: defaultDate(page.year, page.month), note: '' },
    open,
  )
  const range = monthRange(page.year, page.month)
  const refund = v.type === 'REFUND'
  const member = nameOf(page.members, v.memberId)
  const holder = nameOf(page.members, v.cashHolderId)
  const sameHolder = v.memberId && v.memberId === v.cashHolderId

  return (
    <FormModal
      open={open}
      title={refund ? 'Record a refund' : 'Record a payment'}
      onClose={onClose}
      submitLabel={refund ? 'Record refund' : 'Record payment'}
      validate={() =>
        onlyProblems({
          memberId: requiredProblem(v.memberId, 'Choose the member.'),
          cashHolderId: requiredProblem(v.cashHolderId, refund ? 'Choose who paid the money back.' : 'Choose who received the cash.'),
          amount: moneyProblem(v.amount),
          paidOn: dateProblem(v.paidOn, page.year, page.month),
        })
      }
      review={() =>
        refund ? (
          <>
            <p>{holder} hands <strong>{formatTaka(v.amount)}</strong> back to {member} on {shortDate(v.paidOn)}.</p>
            <p>{member}’s credit goes down by {formatTaka(v.amount)}, and the flat cash {holder} holds goes down too.</p>
          </>
        ) : (
          <>
            <p>{member} pays <strong>{formatTaka(v.amount)}</strong> on {shortDate(v.paidOn)}.</p>
            <p>
              What {member} owes goes down by {formatTaka(v.amount)}.{' '}
              {sameHolder
                ? `${member} keeps the cash for the flat.`
                : `${holder} now holds this cash for the flat.`}
            </p>
          </>
        )
      }
      submit={({ allowDuplicate }) =>
        api(`${page.base}/payments`, {
          method: 'POST',
          body: {
            memberId: Number(v.memberId),
            cashHolderId: Number(v.cashHolderId),
            amount: v.amount.trim(),
            type: v.type,
            paidOn: v.paidOn,
            note: v.note.trim() || null,
            allowDuplicate: allowDuplicate || false,
          },
        })
      }
      onDone={onDone}
    >
      {(problems) => (
        <>
          <Choice
            legend="What happened?"
            name="paymentType"
            value={v.type}
            onChange={set('type')}
            options={[
              { value: 'PAYMENT', label: 'A member paid' },
              { value: 'REFUND', label: 'The flat paid a member back' },
            ]}
          />
          <Field label={refund ? 'Paid back to' : 'Who paid?'} problem={problems.memberId}>
            {(props) => <MemberSelect members={page.members} value={v.memberId} onChange={set('memberId')} {...props} />}
          </Field>
          <Field
            label={refund ? 'Who handed the money back?' : 'Who received the cash?'}
            problem={problems.cashHolderId}
            hint={refund ? 'Their flat cash goes down.' : 'They now hold this cash for the flat.'}
          >
            {(props) => <MemberSelect members={page.members} value={v.cashHolderId} onChange={set('cashHolderId')} {...props} />}
          </Field>
          <div className="field-row">
            <Field label="Amount" problem={problems.amount}>
              {(props) => <MoneyInput value={v.amount} onChange={set('amount')} {...props} />}
            </Field>
            <Field label="Date" problem={problems.paidOn}>
              {(props) => <input type="date" min={range.min} max={range.max} value={v.paidOn} onChange={set('paidOn')} {...props} />}
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

function AdjustmentForm({ open, page, onClose, onDone }) {
  const [v, set] = useFormValues({ memberId: '', direction: 'OWES', amount: '', type: 'CORRECTION', reason: '' }, open)
  const member = nameOf(page.members, v.memberId)
  const signed = v.direction === 'CREDIT' ? `-${v.amount.trim()}` : v.amount.trim()

  return (
    <FormModal
      open={open}
      title="Add a balance adjustment"
      onClose={onClose}
      submitLabel="Add adjustment"
      validate={() =>
        onlyProblems({
          memberId: requiredProblem(v.memberId, 'Choose the member.'),
          amount: moneyProblem(v.amount),
          reason: requiredProblem(v.reason, 'Explain the adjustment so everyone understands it later.'),
        })
      }
      review={() => (
        <>
          <p>
            {v.direction === 'CREDIT' ? (
              <>{member} gets <strong className="text-credit">{formatTaka(v.amount)}</strong> of credit.</>
            ) : (
              <>{member} owes <strong className="text-owes">{formatTaka(v.amount)}</strong> more.</>
            )}
          </p>
          <p>{ADJUSTMENT_TYPES[v.type]}: {v.reason.trim()}</p>
          <p className="muted small">This changes {member}’s balance only. It doesn’t move any cash.</p>
        </>
      )}
      submit={() =>
        api(`${page.base}/adjustments`, {
          method: 'POST',
          body: { memberId: Number(v.memberId), amount: signed, type: v.type, reason: v.reason.trim() },
        })
      }
      onDone={onDone}
    >
      {(problems) => (
        <>
          <Field label="Member" problem={problems.memberId}>
            {(props) => <MemberSelect members={page.members} value={v.memberId} onChange={set('memberId')} {...props} />}
          </Field>
          <Choice
            legend="Change"
            name="direction"
            value={v.direction}
            onChange={set('direction')}
            options={[
              { value: 'OWES', label: 'They owe more' },
              { value: 'CREDIT', label: 'Give them credit' },
            ]}
          />
          <div className="field-row">
            <Field label="Amount" problem={problems.amount}>
              {(props) => <MoneyInput value={v.amount} onChange={set('amount')} {...props} />}
            </Field>
            <Field label="Kind" problem={problems.type}>
              {(props) => (
                <select value={v.type} onChange={set('type')} {...props}>
                  {Object.entries(ADJUSTMENT_TYPES).map(([value, label]) => (
                    <option key={value} value={value}>{label}</option>
                  ))}
                </select>
              )}
            </Field>
          </div>
          <Field label="Reason" problem={problems.reason} hint="For example: Balance from the September spreadsheet.">
            {(props) => <input value={v.reason} onChange={set('reason')} maxLength={255} {...props} />}
          </Field>
        </>
      )}
    </FormModal>
  )
}
