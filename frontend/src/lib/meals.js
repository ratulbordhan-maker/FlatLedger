// Meal values and how they are shown.
// Values are kept as strings ("0", "0.5", "2") so they compare exactly.

/** Every value the backend accepts: 0 to 2 in steps of 0.25 */
export const MEAL_VALUES = ['0', '0.25', '0.5', '0.75', '1', '1.25', '1.5', '1.75', '2']

/** The common ones, shown as big buttons on phones */
export const QUICK_VALUES = ['0', '0.25', '0.5', '1', '2']

/** The less common ones, in the "More" menu on phones */
export const OTHER_VALUES = ['0.75', '1.25', '1.5', '1.75']

/** Turn whatever the API sent (2, 2.0, "2.00") into our string form ("2"). */
export function mealKey(value) {
  return String(Number(value ?? 0))
}

const FRACTIONS = { 0.25: '¼', 0.5: '½', 0.75: '¾' }

/** "1½", "¼", "2" */
export function mealShort(value) {
  const number = Number(value)
  const whole = Math.floor(number)
  const fraction = FRACTIONS[number - whole] ?? ''
  if (whole === 0) return fraction || '0'
  return `${whole}${fraction}`
}

/** Words for screen readers and labels: "2 meals", "½ meal", "No meal" */
export function mealWords(value) {
  const number = Number(value)
  if (number === 0) return 'No meal'
  return `${mealShort(value)} ${number > 1 ? 'meals' : 'meal'}`
}

/** Option text in the picker, explaining the common portions */
export function mealOptionLabel(value) {
  switch (value) {
    case '0':
      return '0 (no meal)'
    case '0.25':
      return '¼ (rice, half portion)'
    case '0.5':
      return '½ (rice only)'
    case '1':
      return '1 (lunch or dinner)'
    case '2':
      return '2 (lunch and dinner)'
    default:
      return mealShort(value)
  }
}
