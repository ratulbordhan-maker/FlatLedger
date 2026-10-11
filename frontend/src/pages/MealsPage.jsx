import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { RiceBowl } from '../components/Art.jsx'
import { EmptyCard, ErrorNote, Loading, PageHead } from '../components/Feedback.jsx'
import MealDots from '../components/MealDots.jsx'
import { formatMeals, isoDate } from '../lib/format.js'
import {
  MEAL_VALUES,
  OTHER_VALUES,
  QUICK_VALUES,
  mealKey,
  mealOptionLabel,
  mealShort,
  mealWords,
} from '../lib/meals.js'
import { useApi } from '../lib/useApi.js'
import { useMediaQuery } from '../lib/useMediaQuery.js'

const cellId = (memberId, day) => `${memberId}|${day}`

/**
 * The meal grid.
 * Changes are kept on screen until "Save changes" sends them all at once.
 * The backend saves the whole batch or nothing, so the grid is never half-saved.
 */
export default function MealsPage() {
  const { year, month } = useParams()
  const { isManager } = useAuth()
  const path = `/months/${year}/${month}/meals`
  const grid = useApi(path)
  const isPhone = useMediaQuery('(max-width: 720px)')

  const [pending, setPending] = useState({}) // "memberId|day" → new value
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState(null)
  const [saveError, setSaveError] = useState(null)
  const pendingCount = Object.keys(pending).length

  // Moving to another month starts clean
  useEffect(() => {
    setPending({})
    setNotice(null)
    setSaveError(null)
  }, [path])

  // Warn before closing or reloading the tab with unsaved changes
  useEffect(() => {
    if (pendingCount === 0) return undefined
    const warn = (event) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [pendingCount])

  // "Saved 3 changes." fades after a few seconds
  useEffect(() => {
    if (!notice) return undefined
    const timer = setTimeout(() => setNotice(null), 3500)
    return () => clearTimeout(timer)
  }, [notice])

  if (grid.error) return <ErrorNote error={grid.error} notFoundText="This month hasn’t been opened yet." />
  if (!grid.data) return <Loading what="Loading meals" />

  const g = grid.data
  const editable = isManager && g.status === 'OPEN'

  const savedValue = (row, day) => mealKey(row.meals[day - 1])
  const valueOf = (row, day) => pending[cellId(row.memberId, day)] ?? savedValue(row, day)
  const isChanged = (row, day) => pending[cellId(row.memberId, day)] !== undefined

  function setCell(row, day, value) {
    const original = savedValue(row, day)
    setPending((previous) => {
      const next = { ...previous }
      if (value === original) delete next[cellId(row.memberId, day)]
      else next[cellId(row.memberId, day)] = value
      return next
    })
    setNotice(null)
    setSaveError(null)
  }

  async function save() {
    setSaving(true)
    setSaveError(null)
    const entries = Object.entries(pending).map(([id, value]) => {
      const [memberId, day] = id.split('|').map(Number)
      return { memberId, date: isoDate(g.year, g.month, day), meals: Number(value) }
    })
    try {
      const result = await api(path, { method: 'PUT', body: { entries } })
      grid.setData(result.grid)
      setPending({})
      setNotice(result.changedCells === 1 ? 'Saved 1 change.' : `Saved ${result.changedCells} changes.`)
    } catch (error) {
      setSaveError(error)
    } finally {
      setSaving(false)
    }
  }

  const helpText = !isManager
    ? 'Meals are recorded by the manager.'
    : g.status === 'OPEN'
      ? isPhone
        ? 'Pick a day, tap each person’s meals, then save.'
        : 'Click a cell to change it. Save when you’re done.'
      : null

  return (
    <div className="page meals-page">
      <PageHead art={<RiceBowl className="page-art" />} title="Meals">
        {helpText && <p className="muted">{helpText}</p>}
      </PageHead>

      {g.status === 'CLOSED' && (
        <p className="banner">{g.label} is closed, so its meals can’t be changed.</p>
      )}

      {g.members.length === 0 ? (
        <EmptyCard art={<RiceBowl className="empty-art" />} title="Nobody is in this month yet">
          Members are added to {g.label} when it opens. Add members first, then their meals appear here.
        </EmptyCard>
      ) : (
        <div className="card flush">
          {isPhone ? (
        <DayView
          key={path}
          grid={g}
          editable={editable}
          valueOf={valueOf}
          isChanged={isChanged}
          setCell={setCell}
        />
      ) : (
        <MonthGrid grid={g} editable={editable} valueOf={valueOf} isChanged={isChanged} setCell={setCell} />
          )}
          <Legend />
        </div>
      )}

      {editable && (pendingCount > 0 || notice || saveError) && (
        <div className="save-bar" role="status">
          <span className="save-bar-text">
            {saveError ? (
              <span className="text-error">{saveError.message}</span>
            ) : pendingCount > 0 ? (
              `${pendingCount} unsaved ${pendingCount === 1 ? 'change' : 'changes'}`
            ) : (
              notice
            )}
          </span>
          {pendingCount > 0 && (
            <span className="save-bar-actions">
              <button type="button" className="button quiet" onClick={() => setPending({})} disabled={saving}>
                Discard
              </button>
              <button type="button" className="button primary" onClick={save} disabled={saving}>
                {saving ? 'Saving…' : 'Save changes'}
              </button>
            </span>
          )}
        </div>
      )}
    </div>
  )
}

/** Laptop: the whole month, members down the side, days across the top. */
function MonthGrid({ grid, editable, valueOf, isChanged, setCell }) {
  const days = Array.from({ length: grid.daysInMonth }, (_, i) => i + 1)
  const dateOf = (day) => new Date(grid.year, grid.month - 1, day)
  const isFriday = (day) => dateOf(day).getDay() === 5
  const rowTotal = (row) => days.reduce((sum, day) => sum + Number(valueOf(row, day)), 0)
  const dayTotal = (day) => grid.members.reduce((sum, row) => sum + Number(valueOf(row, day)), 0)
  const grandTotal = grid.members.reduce((sum, row) => sum + rowTotal(row), 0)
  const monthShort = dateOf(1).toLocaleDateString('en-GB', { month: 'short' })

  return (
    <div className="grid-scroll" role="region" aria-label="Meal grid" tabIndex={0}>
      <table className="meal-grid">
        <thead>
          <tr>
            <th scope="col" className="name-col">Member</th>
            {days.map((day) => (
              <th key={day} scope="col" className={isFriday(day) ? 'is-friday' : undefined}>
                <span className="dow">{dateOf(day).toLocaleDateString('en-GB', { weekday: 'narrow' })}</span>
                <span className="dom">{day}</span>
              </th>
            ))}
            <th scope="col" className="total-col">Total</th>
          </tr>
        </thead>
        <tbody>
          {grid.members.map((row) => (
            <tr key={row.memberId}>
              <th scope="row" className="name-col">
                {row.name}
                {row.memberStatus === 'INACTIVE' && <span className="muted small"> (left)</span>}
              </th>
              {days.map((day) => {
                const value = valueOf(row, day)
                const classes = [
                  isFriday(day) ? 'is-friday' : '',
                  isChanged(row, day) ? 'is-changed' : '',
                  value === '0' ? 'is-zero' : '',
                ].join(' ')
                return (
                  <td key={day} className={classes}>
                    <MealCell
                      value={value}
                      editable={editable}
                      label={`${row.name}, ${day} ${monthShort}`}
                      onChange={(next) => setCell(row, day, next)}
                    />
                  </td>
                )
              })}
              <td className="total-col">{formatMeals(rowTotal(row))}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row" className="name-col">Day total</th>
            {days.map((day) => (
              <td key={day} className={isFriday(day) ? 'is-friday' : undefined}>
                {formatMeals(dayTotal(day))}
              </td>
            ))}
            <td className="total-col grand">{formatMeals(grandTotal)}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

/**
 * One cell. For the manager, an invisible dropdown sits on top of the glyph,
 * so clicking (or Tab + arrow keys) opens the normal picker of the device.
 */
function MealCell({ value, editable, label, onChange }) {
  if (!editable) {
    return (
      <span className="cell" title={`${label}: ${mealWords(value)}`}>
        <MealDots value={value} />
        <span className="sr-only">{`${label}: ${mealWords(value)}`}</span>
      </span>
    )
  }
  return (
    <label className="cell cell-edit" title={`${label}: ${mealWords(value)}`}>
      <MealDots value={value} />
      <span className="sr-only">{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        {MEAL_VALUES.map((v) => (
          <option key={v} value={v}>
            {mealOptionLabel(v)}
          </option>
        ))}
      </select>
    </label>
  )
}

/** Phone: one day at a time, with big buttons for each person. */
function DayView({ grid, editable, valueOf, isChanged, setCell }) {
  const today = new Date()
  const startDay =
    today.getFullYear() === grid.year && today.getMonth() + 1 === grid.month ? today.getDate() : 1
  const [day, setDay] = useState(startDay)

  const date = new Date(grid.year, grid.month - 1, day)
  const dateLabel = date.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' })
  const dayTotal = grid.members.reduce((sum, row) => sum + Number(valueOf(row, day)), 0)

  return (
    <section className="day-view" aria-label="Meals for one day">
      <div className="day-stepper">
        <button type="button" className="step" onClick={() => setDay(day - 1)} disabled={day === 1} aria-label="Previous day">
          ‹
        </button>
        <div className="day-title">
          <strong>{dateLabel}</strong>
          <span className="muted small">{formatMeals(dayTotal)} meals that day</span>
        </div>
        <button
          type="button"
          className="step"
          onClick={() => setDay(day + 1)}
          disabled={day === grid.daysInMonth}
          aria-label="Next day"
        >
          ›
        </button>
      </div>

      {editable && (
        <button
          type="button"
          className="button quiet small fill-day"
          onClick={() => grid.members.forEach((row) => setCell(row, day, '2'))}
        >
          Everyone had lunch and dinner
        </button>
      )}

      <ul className="day-list">
        {grid.members.map((row) => {
          const value = valueOf(row, day)
          return (
            <li key={row.memberId} className={isChanged(row, day) ? 'is-changed' : undefined}>
              <div className="day-person">
                <span className="day-name">{row.name}</span>
                <MealDots value={value} size={13} />
                <span className="muted small">{mealWords(value)}</span>
              </div>
              {editable && (
                <div className="segmented" role="radiogroup" aria-label={`Meals for ${row.name}`}>
                  {QUICK_VALUES.map((option) => (
                    <button
                      key={option}
                      type="button"
                      role="radio"
                      aria-checked={value === option}
                      className={value === option ? 'is-on' : undefined}
                      onClick={() => setCell(row, day, option)}
                    >
                      {mealShort(option)}
                    </button>
                  ))}
                  <select
                    aria-label={`Other amount for ${row.name}`}
                    className={OTHER_VALUES.includes(value) ? 'is-on' : undefined}
                    value={OTHER_VALUES.includes(value) ? value : ''}
                    onChange={(e) => e.target.value && setCell(row, day, e.target.value)}
                  >
                    <option value="">More</option>
                    {OTHER_VALUES.map((option) => (
                      <option key={option} value={option}>
                        {mealShort(option)}
                      </option>
                    ))}
                  </select>
                </div>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}

function Legend() {
  return (
    <ul className="legend" aria-label="What the dots mean">
      <li><MealDots value="2" /> lunch and dinner (2)</li>
      <li><MealDots value="1" /> lunch or dinner (1)</li>
      <li><MealDots value="0.5" /> rice only (½)</li>
      <li><MealDots value="0.25" /> rice, half portion (¼)</li>
      <li><MealDots value="0" /> no meal</li>
    </ul>
  )
}
