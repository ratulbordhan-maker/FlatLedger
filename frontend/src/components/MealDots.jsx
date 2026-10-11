/**
 * Draws a meal value as dots: one full circle per meal, plus a part-filled
 * circle for ¼, ½ or ¾.   2 = ●●   1½ = ● + half circle   0 = a tiny dot
 *
 * Drawn with SVG (not text symbols) so it looks the same on every phone and laptop.
 * Screen readers skip it; the words are provided next to it.
 */
export default function MealDots({ value, size = 11 }) {
  const number = Number(value)
  if (number === 0) return <span className="dots dots-none" aria-hidden="true" />

  const whole = Math.floor(number)
  const part = number - whole
  const dots = []
  for (let i = 0; i < whole; i++) dots.push(<Dot key={i} fraction={1} size={size} />)
  if (part > 0) dots.push(<Dot key="part" fraction={part} size={size} />)

  return (
    <span className="dots" aria-hidden="true">
      {dots}
    </span>
  )
}

function Dot({ fraction, size }) {
  const center = 6
  const radius = 5
  if (fraction >= 1) {
    return (
      <svg width={size} height={size} viewBox="0 0 12 12" focusable="false">
        <circle cx={center} cy={center} r={radius} fill="currentColor" />
      </svg>
    )
  }
  // A pie slice starting at 12 o'clock, going clockwise
  const angle = 2 * Math.PI * fraction
  const x = center + radius * Math.sin(angle)
  const y = center - radius * Math.cos(angle)
  const largeArc = fraction > 0.5 ? 1 : 0
  return (
    <svg width={size} height={size} viewBox="0 0 12 12" focusable="false">
      <circle cx={center} cy={center} r={radius - 0.5} fill="none" stroke="currentColor" strokeWidth="1" />
      <path
        d={`M${center} ${center} L${center} ${center - radius} A${radius} ${radius} 0 ${largeArc} 1 ${x.toFixed(3)} ${y.toFixed(3)} Z`}
        fill="currentColor"
      />
    </svg>
  )
}
