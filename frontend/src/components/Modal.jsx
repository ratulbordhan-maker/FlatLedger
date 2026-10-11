import { useEffect, useId, useRef, useState } from 'react'
import { ErrorNote } from './Feedback.jsx'

/**
 * A pop-up window built on the browser's own <dialog>, which keeps keyboard
 * focus inside it and closes with Escape. On phones it fills the screen.
 */
export function Modal({ open, title, onClose, children }) {
  const ref = useRef(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
    >
      {open && (
        <div className="modal-body">
          <div className="modal-head">
            <h2 id={titleId}>{title}</h2>
            <button type="button" className="icon-button" onClick={onClose} aria-label="Close">
              ×
            </button>
          </div>
          {children}
        </div>
      )}
    </dialog>
  )
}

/**
 * Every money form works in two steps:
 *   1. fill in the form, then "Review";
 *   2. read a plain-language summary of what will happen, then confirm.
 * If the server says it looks like a duplicate, the person can choose "Save anyway".
 *
 * children is a function that receives the field problems: children(problems)
 */
export function FormModal({ open, title, onClose, validate, review, submit, submitLabel, onDone, children }) {
  const [step, setStep] = useState('edit')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [problems, setProblems] = useState({})
  const [duplicate, setDuplicate] = useState(false)

  useEffect(() => {
    if (open) {
      setStep('edit')
      setError(null)
      setProblems({})
      setDuplicate(false)
    }
  }, [open])

  function goToReview(event) {
    event.preventDefault()
    const found = validate()
    setProblems(found)
    if (Object.keys(found).length === 0) {
      setError(null)
      setStep('review')
    }
  }

  async function send(options = {}) {
    setBusy(true)
    setError(null)
    try {
      const result = await submit(options)
      onDone(result)
    } catch (err) {
      if (err.status === 409 && /allowDuplicate/.test(err.message)) {
        setDuplicate(true)
      } else if (Object.keys(err.fieldErrors || {}).length > 0) {
        setProblems(err.fieldErrors)
        setError(err)
        setStep('edit')
      } else {
        setError(err)
      }
    } finally {
      setBusy(false)
    }
  }

  const close = () => {
    if (!busy) onClose()
  }

  return (
    <Modal open={open} title={title} onClose={close}>
      {step === 'edit' ? (
        <form onSubmit={goToReview} noValidate>
          {children(problems)}
          {error && <ErrorNote error={error} />}
          <div className="modal-actions">
            <button type="button" className="button quiet" onClick={close}>
              Cancel
            </button>
            <button type="submit" className="button primary">
              Review
            </button>
          </div>
        </form>
      ) : (
        <div>
          <div className="review">{review()}</div>
          {duplicate && (
            <p className="warning-note">
              This looks like a duplicate: the same amount for the same person on the same day is already recorded.
              Save it only if it really happened twice.
            </p>
          )}
          {error && <ErrorNote error={error} />}
          <div className="modal-actions">
            <button
              type="button"
              className="button quiet"
              onClick={() => {
                setStep('edit')
                setDuplicate(false)
              }}
              disabled={busy}
            >
              Back
            </button>
            {duplicate ? (
              <button type="button" className="button primary" onClick={() => send({ allowDuplicate: true })} disabled={busy}>
                {busy ? 'Saving…' : 'Save anyway'}
              </button>
            ) : (
              <button type="button" className="button primary" onClick={() => send()} disabled={busy}>
                {busy ? 'Saving…' : submitLabel}
              </button>
            )}
          </div>
        </div>
      )}
    </Modal>
  )
}

/**
 * Cancelling an entry (the backend calls it "void"). Nothing is deleted:
 * the entry stays in the history, crossed out, with the reason.
 */
export function CancelEntryDialog({
  open,
  title,
  summary,
  onClose,
  onConfirm,
  note = 'It stays in the history, crossed out, and stops counting in every total.',
  confirmLabel = 'Cancel entry',
  busyLabel = 'Cancelling…',
  keepLabel = 'Keep it',
  danger = true,
}) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [problem, setProblem] = useState(null)
  const fieldId = useId()

  useEffect(() => {
    if (open) {
      setReason('')
      setError(null)
      setProblem(null)
    }
  }, [open])

  async function handleSubmit(event) {
    event.preventDefault()
    if (!reason.trim()) {
      setProblem('Write a short reason, for example “Entered twice”.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await onConfirm(reason.trim())
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title={title} onClose={() => !busy && onClose()}>
      <form onSubmit={handleSubmit} noValidate>
        <p className="review">{summary}</p>
        {note && <p className="muted small">{note}</p>}
        <div className="field">
          <label htmlFor={fieldId}>Reason</label>
          <input
            id={fieldId}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={255}
            aria-invalid={Boolean(problem)}
            autoFocus
          />
          {problem && <span className="field-problem">{problem}</span>}
        </div>
        {error && <ErrorNote error={error} />}
        <div className="modal-actions">
          <button type="button" className="button quiet" onClick={onClose} disabled={busy}>
            {keepLabel}
          </button>
          <button type="submit" className={`button ${danger ? 'danger' : 'primary'}`} disabled={busy}>
            {busy ? busyLabel : confirmLabel}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/** A yes/no question for actions that are easy to explain but important. */
export function ConfirmDialog({ open, title, children, confirmLabel, busyLabel = 'Working…', onClose, onConfirm, danger = false }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (open) setError(null)
  }, [open])

  async function confirm() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} title={title} onClose={() => !busy && onClose()}>
      <div className="review">{children}</div>
      {error && <ErrorNote error={error} />}
      <div className="modal-actions">
        <button type="button" className="button quiet" onClick={onClose} disabled={busy}>
          Not now
        </button>
        <button type="button" className={`button ${danger ? 'danger' : 'primary'}`} onClick={confirm} disabled={busy}>
          {busy ? busyLabel : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
