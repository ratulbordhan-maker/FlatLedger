import { useState } from 'react'
import { api } from '../api/client.js'
import { Icons, Thali } from '../components/Art.jsx'
import { ErrorNote, Loading, PageHead } from '../components/Feedback.jsx'
import { Choice, Field } from '../components/Fields.jsx'
import { ConfirmDialog, FormModal } from '../components/Modal.jsx'
import { isoDate, shortDate } from '../lib/format.js'
import { onlyProblems, requiredProblem, useFormValues } from '../lib/forms.js'
import { useApi } from '../lib/useApi.js'
import { useMonthPage } from '../lib/useMonthPage.js'

/** Today's date in the flat's own time zone (not UTC). */
const today = () => {
  const now = new Date()
  return isoDate(now.getFullYear(), now.getMonth() + 1, now.getDate())
}

/**
 * Manager only. Who lives in the flat, who is part of this month, and who can sign in.
 * Nobody is ever deleted: people who leave are marked as left, and their history stays.
 */
export default function MembersPage() {
  const page = useMonthPage()
  const logins = useApi('/members/logins')
  const [dialog, setDialog] = useState(null) // { kind, member }

  if (logins.error) return <ErrorNote error={logins.error} />
  if (!page.monthInfo.data || !logins.data) return <Loading what="Loading members" />

  const inMonth = new Set(page.monthInfo.data.members.map((m) => m.id))
  const loginFor = (memberId) => logins.data.find((l) => l.memberId === memberId)
  const sorted = [...page.members].sort((a, b) => {
    if (a.status !== b.status) return a.status === 'ACTIVE' ? -1 : 1
    return a.name.localeCompare(b.name)
  })
  const open = (kind, member = null) => setDialog({ kind, member })
  const close = () => setDialog(null)
  const done = (message) => {
    page.setFlash(message)
    setDialog(null)
    page.reloadMembers()
    page.monthInfo.reload()
    logins.reload()
  }
  const target = dialog?.member

  return (
    <div className="page">
      <PageHead
        art={<Thali className="page-art" />}
        title="Members"
        action={
          <button type="button" className="button primary" onClick={() => open('add')}>
            Add member
          </button>
        }
      >
        <p className="muted">
          {page.members.filter((m) => m.status === 'ACTIVE').length} living in the flat, {inMonth.size} in {page.label}.
        </p>
      </PageHead>

      {page.flash && <p className="flash" role="status">{page.flash}</p>}

      <div className="card flush">
        <table className="ledger records">
          <thead>
            <tr>
              <th scope="col">Member</th>
              <th scope="col">{page.label}</th>
              <th scope="col">Sign-in</th>
              <th scope="col"><span className="sr-only">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((m) => {
              const login = loginFor(m.id)
              const left = m.status === 'INACTIVE'
              return (
                <tr key={m.id}>
                  <th scope="row">
                    <span className="person">
                      <span className={`avatar ${left ? 'is-left' : ''}`} aria-hidden="true">{m.name.charAt(0).toUpperCase()}</span>
                      <span className="cell-stack">
                        <span className="record-title">{m.name}</span>
                        <span className="record-note">
                          {left ? `Left on ${shortDate(m.leftOn)}` : `Joined ${shortDate(m.joinedOn)}`}
                        </span>
                      </span>
                    </span>
                  </th>
                  <td data-label={page.label}>
                    {inMonth.has(m.id) ? (
                      <span className="tag tag-in">In this month</span>
                    ) : !left && page.isOpen ? (
                      <button type="button" className="link-button" onClick={() => open('addToMonth', m)}>
                        Add to {page.label}
                      </button>
                    ) : (
                      <span className="muted">Not in this month</span>
                    )}
                  </td>
                  <td data-label="Sign-in">
                    {login ? (
                      <span className="cell-stack">
                        <span className="record-title">{login.username}</span>
                        <span className="record-note">{login.role === 'MANAGER' ? 'Manager' : 'Member'}</span>
                      </span>
                    ) : (
                      <button type="button" className="link-button" onClick={() => open('login', m)}>
                        Create sign-in
                      </button>
                    )}
                  </td>
                  <td className="actions">
                    <button type="button" className="link-button" onClick={() => open('rename', m)}>
                      Rename
                    </button>
                    {login && (
                      <button type="button" className="link-button" onClick={() => open('password', m)}>
                        New password
                      </button>
                    )}
                    {left ? (
                      <button type="button" className="link-button" onClick={() => open('reactivate', m)}>
                        Bring back
                      </button>
                    ) : (
                      <button type="button" className="link-button danger-link" onClick={() => open('leave', m)}>
                        Mark as left
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <p className="muted small note-line">
        <Icons.members /> Marking someone as left keeps all their meals, payments and balances. They simply won’t be added to new months.
      </p>

      <AddMemberForm open={dialog?.kind === 'add'} page={page} onClose={close} onDone={(name) => done(`${name} added.`)} />
      <RenameForm open={dialog?.kind === 'rename'} member={target} onClose={close} onDone={() => done('Name updated.')} />
      <LoginForm open={dialog?.kind === 'login'} member={target} onClose={close} onDone={() => done('Sign-in created.')} />
      <PasswordForm open={dialog?.kind === 'password'} member={target} onClose={close} onDone={() => done('Password changed.')} />
      <LeaveForm open={dialog?.kind === 'leave'} member={target} onClose={close} onDone={() => done(`${target?.name} marked as left.`)} />

      <ConfirmDialog
        open={dialog?.kind === 'reactivate'}
        title={`Bring ${target?.name} back?`}
        confirmLabel="Bring back"
        onClose={close}
        onConfirm={async () => {
          await api(`/members/${target.id}/reactivate`, { method: 'POST' })
          done(`${target.name} is active again.`)
        }}
      >
        <p>{target?.name} will be active again and added to new months. Use “Add to {page.label}” if they’re eating this month.</p>
      </ConfirmDialog>

      <ConfirmDialog
        open={dialog?.kind === 'addToMonth'}
        title={`Add ${target?.name} to ${page.label}?`}
        confirmLabel="Add to month"
        onClose={close}
        onConfirm={async () => {
          await api(`${page.base}/members`, { method: 'POST', body: { memberId: target.id } })
          done(`${target.name} added to ${page.label}.`)
        }}
      >
        <p>{target?.name} gets a row in the {page.label} meal grid and shares its meal rate.</p>
      </ConfirmDialog>
    </div>
  )
}

function AddMemberForm({ open, page, onClose, onDone }) {
  const [v, set] = useFormValues({ name: '', joinedOn: today(), addToMonth: true }, open)

  return (
    <FormModal
      open={open}
      title="Add a member"
      onClose={onClose}
      submitLabel="Add member"
      validate={() => onlyProblems({ name: requiredProblem(v.name, 'Enter their name.'), joinedOn: requiredProblem(v.joinedOn, 'Choose the date they joined.') })}
      review={() => (
        <>
          <p><strong>{v.name.trim()}</strong> joins the flat on {shortDate(v.joinedOn)}.</p>
          <p>
            {v.addToMonth && page.isOpen
              ? `They’ll be added to ${page.label} straight away, so you can record their meals.`
              : 'They’ll be added to the next month you open.'}
          </p>
          <p className="muted small">You can create their sign-in afterwards.</p>
        </>
      )}
      submit={async () => {
        const member = await api('/members', { method: 'POST', body: { name: v.name.trim(), joinedOn: v.joinedOn } })
        if (v.addToMonth && page.isOpen) {
          await api(`${page.base}/members`, { method: 'POST', body: { memberId: member.id } })
        }
        return member
      }}
      onDone={(member) => onDone(member.name)}
    >
      {(problems) => (
        <>
          <Field label="Name" problem={problems.name}>
            {(props) => <input value={v.name} onChange={set('name')} maxLength={100} autoComplete="off" {...props} />}
          </Field>
          <Field label="Joined on" problem={problems.joinedOn}>
            {(props) => <input type="date" value={v.joinedOn} onChange={set('joinedOn')} {...props} />}
          </Field>
          {page.isOpen && (
            <label className="check">
              <input type="checkbox" checked={v.addToMonth} onChange={set('addToMonth')} />
              <span>Add them to {page.label} too</span>
            </label>
          )}
        </>
      )}
    </FormModal>
  )
}

function RenameForm({ open, member, onClose, onDone }) {
  const [v, set] = useFormValues({ name: member?.name ?? '' }, open)
  return (
    <FormModal
      open={open}
      title="Rename member"
      onClose={onClose}
      submitLabel="Save name"
      validate={() => onlyProblems({ name: requiredProblem(v.name, 'Enter a name.') })}
      review={() => <p>{member?.name} will be shown as <strong>{v.name.trim()}</strong> everywhere, including past months.</p>}
      submit={() => api(`/members/${member.id}`, { method: 'PUT', body: { name: v.name.trim() } })}
      onDone={onDone}
    >
      {(problems) => (
        <Field label="Name" problem={problems.name}>
          {(props) => <input value={v.name} onChange={set('name')} maxLength={100} {...props} />}
        </Field>
      )}
    </FormModal>
  )
}

function LoginForm({ open, member, onClose, onDone }) {
  const suggested = (member?.name ?? '').toLowerCase().replace(/[^a-z0-9._-]/g, '')
  const [v, set] = useFormValues({ username: suggested, password: '', role: 'MEMBER' }, open)

  return (
    <FormModal
      open={open}
      title={`Create a sign-in for ${member?.name ?? ''}`}
      onClose={onClose}
      submitLabel="Create sign-in"
      validate={() =>
        onlyProblems({
          username: !/^[A-Za-z0-9._-]{3,50}$/.test(v.username.trim())
            ? '3 to 50 characters: letters, numbers, dot, dash or underscore.'
            : null,
          password: v.password.length < 8 ? 'Use at least 8 characters.' : null,
        })
      }
      review={() => (
        <>
          <p>{member?.name} signs in as <strong>{v.username.trim()}</strong>.</p>
          <p>{v.role === 'MANAGER' ? 'They can change everything, like you.' : 'They can see their own account and the shared meals, but can’t change anything.'}</p>
          <p className="muted small">Share the password with them privately. They can change it after signing in.</p>
        </>
      )}
      submit={() => api(`/members/${member.id}/login`, { method: 'POST', body: { username: v.username.trim(), password: v.password, role: v.role } })}
      onDone={onDone}
    >
      {(problems) => (
        <>
          <Field label="Username" problem={problems.username}>
            {(props) => <input value={v.username} onChange={set('username')} autoCapitalize="none" autoComplete="off" {...props} />}
          </Field>
          <Field label="Password" problem={problems.password} hint="At least 8 characters.">
            {(props) => <input type="text" value={v.password} onChange={set('password')} autoComplete="new-password" {...props} />}
          </Field>
          <Choice
            legend="They can"
            name="role"
            value={v.role}
            onChange={set('role')}
            options={[
              { value: 'MEMBER', label: 'View only' },
              { value: 'MANAGER', label: 'Manage everything' },
            ]}
          />
        </>
      )}
    </FormModal>
  )
}

function PasswordForm({ open, member, onClose, onDone }) {
  const [v, set] = useFormValues({ password: '' }, open)
  return (
    <FormModal
      open={open}
      title={`New password for ${member?.name ?? ''}`}
      onClose={onClose}
      submitLabel="Set password"
      validate={() => onlyProblems({ password: v.password.length < 8 ? 'Use at least 8 characters.' : null })}
      review={() => <p>{member?.name}’s old password stops working. Share the new one with them privately.</p>}
      submit={() => api(`/members/${member.id}/login/reset-password`, { method: 'POST', body: { newPassword: v.password } })}
      onDone={onDone}
    >
      {(problems) => (
        <Field label="New password" problem={problems.password} hint="At least 8 characters.">
          {(props) => <input type="text" value={v.password} onChange={set('password')} autoComplete="new-password" {...props} />}
        </Field>
      )}
    </FormModal>
  )
}

function LeaveForm({ open, member, onClose, onDone }) {
  const [v, set] = useFormValues({ leftOn: today() }, open)
  return (
    <FormModal
      open={open}
      title={`Mark ${member?.name ?? ''} as left`}
      onClose={onClose}
      submitLabel="Mark as left"
      validate={() =>
        onlyProblems({
          leftOn: !v.leftOn ? 'Choose the date they left.' : member && v.leftOn < member.joinedOn ? 'They can’t leave before they joined.' : null,
        })
      }
      review={() => (
        <>
          <p>{member?.name} left on {shortDate(v.leftOn)}.</p>
          <p>Their meals, payments and balance stay exactly as they are. They won’t be added to new months. If they still owe money or have credit, it keeps showing on their account.</p>
        </>
      )}
      submit={() => api(`/members/${member.id}/deactivate`, { method: 'POST', body: { leftOn: v.leftOn } })}
      onDone={onDone}
    >
      {(problems) => (
        <Field label="Left on" problem={problems.leftOn}>
          {(props) => <input type="date" min={member?.joinedOn} value={v.leftOn} onChange={set('leftOn')} {...props} />}
        </Field>
      )}
    </FormModal>
  )
}
