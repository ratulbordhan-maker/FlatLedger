// Form fields with a label, an optional hint and a problem message.
import { useId } from 'react'

export function Field({ label, problem, hint, children }) {
  const id = useId()
  const hintId = `${id}-hint`
  const problemId = `${id}-problem`
  // children is a function so the input gets the right id and aria attributes
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children({
        id,
        'aria-invalid': Boolean(problem),
        'aria-describedby': [hint && hintId, problem && problemId].filter(Boolean).join(' ') || undefined,
      })}
      {hint && (
        <span id={hintId} className="hint">
          {hint}
        </span>
      )}
      {problem && (
        <span id={problemId} className="field-problem">
          {problem}
        </span>
      )}
    </div>
  )
}

export function MoneyInput({ value, onChange, ...rest }) {
  return (
    <div className="money-input">
      <span aria-hidden="true">৳</span>
      <input inputMode="decimal" autoComplete="off" placeholder="0.00" value={value} onChange={onChange} {...rest} />
    </div>
  )
}

/** A list of flat members; people who have left are marked and listed last. */
export function MemberSelect({ members, value, onChange, placeholder = 'Choose a person', ...rest }) {
  const sorted = [...(members || [])].sort((a, b) => {
    if (a.status !== b.status) return a.status === 'ACTIVE' ? -1 : 1
    return a.name.localeCompare(b.name)
  })
  return (
    <select value={value} onChange={onChange} {...rest}>
      <option value="">{placeholder}</option>
      {sorted.map((m) => (
        <option key={m.id} value={m.id}>
          {m.name}
          {m.status === 'INACTIVE' ? ' (left)' : ''}
        </option>
      ))}
    </select>
  )
}

/** Two or three choices shown as large buttons, e.g. "From flat cash" / "Own money". */
export function Choice({ legend, name, value, onChange, options, hint }) {
  return (
    <fieldset className="choice">
      <legend>{legend}</legend>
      <div className="choice-options">
        {options.map((option) => (
          <label key={option.value} className={value === option.value ? 'is-on' : undefined}>
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={value === option.value}
              onChange={() => onChange(option.value)}
            />
            <span>{option.label}</span>
          </label>
        ))}
      </div>
      {hint && <span className="hint">{hint}</span>}
    </fieldset>
  )
}
