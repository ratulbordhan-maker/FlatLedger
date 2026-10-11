import { useState } from 'react'
import { api } from '../api/client.js'
import { Basket } from '../components/Art.jsx'
import { EmptyCard, ErrorNote, Loading, PageHead } from '../components/Feedback.jsx'
import { Choice, Field, MemberSelect, MoneyInput } from '../components/Fields.jsx'
import { CancelEntryDialog, FormModal } from '../components/Modal.jsx'
import { formatTaka, shortDate } from '../lib/format.js'
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

const CATEGORY_SUGGESTIONS = ['Groceries', 'Rice', 'Vegetables', 'Fish', 'Meat', 'Eggs', 'Oil and spices', 'Gas', 'Cook']

/**
 * The month's shared spending. Everyone can see it; only the manager changes it.
 */
export default function ExpensesPage() {
  const page = useMonthPage()
  const expenses = useApi(`${page.base}/expenses`)
  const [form, setForm] = useState(null) // null, { expense: null } for new, { expense } to edit
  const [cancelling, setCancelling] = useState(null)

  if (expenses.error) return <ErrorNote error={expenses.error} notFoundText="This month hasn’t been opened yet." />
  if (!expenses.data) return <Loading what="Loading expenses" />

  const active = expenses.data.filter((e) => e.status === 'ACTIVE')
  const mealTotal = active.filter((e) => e.inMealRate).reduce((sum, e) => sum + Number(e.amount), 0)
  const otherTotal = active.filter((e) => !e.inMealRate).reduce((sum, e) => sum + Number(e.amount), 0)

  return (
    <div className="page">
      <PageHead
        art={<Basket className="page-art" />}
        title="Expenses"
        action={
          page.canChange && (
            <button type="button" className="button primary" onClick={() => setForm({ expense: null })}>
              Add expense
            </button>
          )
        }
      >
          <p className="muted">
            Meal expenses <strong className="ink">{formatTaka(mealTotal)}</strong>
            {otherTotal > 0 && (
              <>
                <span className="gap" />
                Other shared costs <strong className="ink">{formatTaka(otherTotal)}</strong> (not in the meal rate)
              </>
            )}
          </p>
      </PageHead>

      {page.flash && <p className="flash" role="status">{page.flash}</p>}
      {page.isManager && page.monthInfo.data && !page.isOpen && (
        <p className="banner">{page.label} is closed, so its expenses can’t be changed.</p>
      )}

      {expenses.data.length === 0 ? (
        <EmptyCard
          art={<Basket className="empty-art" />}
          title="No expenses yet"
          action={
            page.canChange && (
              <button type="button" className="button primary" onClick={() => setForm({ expense: null })}>
                Add the first expense
              </button>
            )
          }
        >
          Groceries and other shared food costs go here. They set this month’s meal rate.
        </EmptyCard>
      ) : (
        <div className="card flush">
        <table className="ledger records">
          <thead>
            <tr>
              <th scope="col">Date</th>
              <th scope="col">What</th>
              <th scope="col">Paid by</th>
              <th scope="col" className="num">Amount</th>
              {page.canChange && <th scope="col"><span className="sr-only">Actions</span></th>}
            </tr>
          </thead>
          <tbody>
            {expenses.data.map((e) => {
              const cancelled = e.status === 'VOIDED'
              return (
                <tr key={e.id} className={cancelled ? 'is-voided' : undefined}>
                  <td data-label="Date">{shortDate(e.expenseDate)}</td>
                  <th scope="row">
                    <span className="record-title">{e.category}</span>
                    {e.description && <span className="record-note">{e.description}</span>}
                    {!e.inMealRate && <span className="record-note">Not in the meal rate</span>}
                    {cancelled && <span className="record-note cancel-note">Cancelled: {e.voidReason}</span>}
                  </th>
                  <td data-label="Paid by">
                    {nameOf(page.members, e.holderMemberId)}{' '}
                    <span className={`source ${e.fundedBy === 'FLAT_CASH' ? 'source-cash' : 'source-pocket'}`}>
                      {e.fundedBy === 'FLAT_CASH' ? 'flat cash' : 'own money'}
                    </span>
                  </td>
                  <td className="num amount" data-label="Amount">{formatTaka(e.amount)}</td>
                  {page.canChange && (
                    <td className="actions">
                      {!cancelled && (
                        <>
                          <button type="button" className="link-button" onClick={() => setForm({ expense: e })}>
                            Edit
                          </button>
                          <button type="button" className="link-button danger-link" onClick={() => setCancelling(e)}>
                            Cancel
                          </button>
                        </>
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

      <ExpenseForm
        open={form !== null}
        expense={form?.expense ?? null}
        page={page}
        onClose={() => setForm(null)}
        onDone={() => {
          page.setFlash(form?.expense ? 'Expense updated.' : 'Expense added.')
          setForm(null)
          expenses.reload()
        }}
      />

      <CancelEntryDialog
        open={cancelling !== null}
        title="Cancel this expense?"
        summary={
          cancelling &&
          `${cancelling.category}, ${formatTaka(cancelling.amount)} on ${shortDate(cancelling.expenseDate)}, paid by ${nameOf(page.members, cancelling.holderMemberId)}.`
        }
        onClose={() => setCancelling(null)}
        onConfirm={async (reason) => {
          await api(`${page.base}/expenses/${cancelling.id}/void`, { method: 'POST', body: { reason } })
          page.setFlash('Expense cancelled.')
          setCancelling(null)
          expenses.reload()
        }}
      />
    </div>
  )
}

function ExpenseForm({ open, expense, page, onClose, onDone }) {
  const editing = Boolean(expense)
  const [v, set] = useFormValues(
    {
      expenseDate: expense?.expenseDate ?? defaultDate(page.year, page.month),
      category: expense?.category ?? '',
      description: expense?.description ?? '',
      amount: expense ? String(Number(expense.amount)) : '',
      fundedBy: expense?.fundedBy ?? 'FLAT_CASH',
      holderMemberId: expense ? String(expense.holderMemberId) : '',
      inMealRate: expense?.inMealRate ?? true,
    },
    open,
  )
  const range = monthRange(page.year, page.month)
  const payer = nameOf(page.members, v.holderMemberId)

  return (
    <FormModal
      open={open}
      title={editing ? 'Edit expense' : 'Add expense'}
      onClose={onClose}
      submitLabel={editing ? 'Save changes' : 'Add expense'}
      validate={() =>
        onlyProblems({
          expenseDate: dateProblem(v.expenseDate, page.year, page.month),
          category: requiredProblem(v.category, 'Say what it was for, for example Groceries.'),
          amount: moneyProblem(v.amount),
          holderMemberId: requiredProblem(v.holderMemberId, 'Choose who paid.'),
        })
      }
      review={() => (
        <>
          <p>
            <strong>{formatTaka(v.amount)}</strong> for {v.category.trim()} on {shortDate(v.expenseDate)}.
          </p>
          {v.fundedBy === 'FLAT_CASH' ? (
            <p>{payer} paid from the flat’s cash, so the cash {payer} holds goes down by {formatTaka(v.amount)}.</p>
          ) : (
            <p>{payer} paid with their own money, so {payer}’s account gets {formatTaka(v.amount)} of credit.</p>
          )}
          <p>{v.inMealRate ? 'It counts toward this month’s meal rate.' : 'It does not count toward the meal rate.'}</p>
        </>
      )}
      submit={({ allowDuplicate }) =>
        api(editing ? `${page.base}/expenses/${expense.id}` : `${page.base}/expenses`, {
          method: editing ? 'PUT' : 'POST',
          body: {
            expenseDate: v.expenseDate,
            category: v.category.trim(),
            description: v.description.trim() || null,
            amount: v.amount.trim(),
            inMealRate: v.inMealRate,
            fundedBy: v.fundedBy,
            holderMemberId: Number(v.holderMemberId),
            allowDuplicate: allowDuplicate || false,
          },
        })
      }
      onDone={onDone}
    >
      {(problems) => (
        <>
          <div className="field-row">
            <Field label="Date" problem={problems.expenseDate}>
              {(props) => <input type="date" min={range.min} max={range.max} value={v.expenseDate} onChange={set('expenseDate')} {...props} />}
            </Field>
            <Field label="Amount" problem={problems.amount}>
              {(props) => <MoneyInput value={v.amount} onChange={set('amount')} {...props} />}
            </Field>
          </div>
          <Field label="What was it for?" problem={problems.category}>
            {(props) => (
              <>
                <input list="expense-categories" value={v.category} onChange={set('category')} maxLength={50} {...props} />
                <datalist id="expense-categories">
                  {CATEGORY_SUGGESTIONS.map((c) => (
                    <option key={c} value={c} />
                  ))}
                </datalist>
              </>
            )}
          </Field>
          <Field label="Note (optional)" problem={problems.description}>
            {(props) => <input value={v.description} onChange={set('description')} maxLength={255} {...props} />}
          </Field>
          <Choice
            legend="Paid with"
            name="fundedBy"
            value={v.fundedBy}
            onChange={set('fundedBy')}
            options={[
              { value: 'FLAT_CASH', label: 'Flat cash' },
              { value: 'MEMBER_POCKET', label: 'Their own money' },
            ]}
            hint={
              v.fundedBy === 'FLAT_CASH'
                ? 'Money someone was holding for the flat.'
                : 'They will get this amount back as credit on their account.'
            }
          />
          <Field label="Who paid?" problem={problems.holderMemberId}>
            {(props) => (
              <MemberSelect members={page.members} value={v.holderMemberId} onChange={set('holderMemberId')} {...props} />
            )}
          </Field>
          <label className="check">
            <input type="checkbox" checked={v.inMealRate} onChange={set('inMealRate')} />
            <span>Count toward the meal rate</span>
          </label>
        </>
      )}
    </FormModal>
  )
}
