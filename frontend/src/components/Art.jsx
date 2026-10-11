// Hand-drawn food illustrations and icons, as SVG so they stay sharp on every
// screen and need no image files. Colours come from CSS variables in index.css.

/** A thali: rice, dal, a green chilli, lemon and a leaf. Used on the overview and login. */
export function Thali({ className }) {
  return (
    <svg className={className} viewBox="0 0 240 200" aria-hidden="true" focusable="false">
      <ellipse cx="120" cy="178" rx="96" ry="10" fill="var(--shadow-ground)" />
      {/* banana leaf */}
      <path d="M22 120 C 40 40, 200 20, 226 96 C 200 150, 60 170, 22 120 Z" fill="var(--leaf)" />
      <path d="M30 118 C 90 92, 160 78, 220 96" stroke="var(--leaf-vein)" strokeWidth="2.5" fill="none" />
      {/* plate */}
      <circle cx="122" cy="108" r="66" fill="var(--plate)" stroke="var(--plate-rim)" strokeWidth="4" />
      {/* rice */}
      <path d="M78 116 C 78 86, 128 78, 138 100 C 146 116, 120 132, 98 130 C 86 129, 78 124, 78 116 Z" fill="#fffdf6" stroke="var(--rice-line)" strokeWidth="1.5" />
      <g fill="var(--rice-line)">
        <ellipse cx="96" cy="104" rx="3" ry="1.4" transform="rotate(-20 96 104)" />
        <ellipse cx="110" cy="96" rx="3" ry="1.4" transform="rotate(15 110 96)" />
        <ellipse cx="118" cy="112" rx="3" ry="1.4" transform="rotate(-35 118 112)" />
        <ellipse cx="102" cy="118" rx="3" ry="1.4" transform="rotate(25 102 118)" />
      </g>
      {/* dal bowl */}
      <circle cx="152" cy="82" r="20" fill="var(--bowl)" />
      <circle cx="152" cy="82" r="14" fill="var(--turmeric)" />
      <circle cx="147" cy="78" r="2" fill="var(--leaf-dark)" />
      <circle cx="157" cy="85" r="1.6" fill="var(--leaf-dark)" />
      {/* lemon */}
      <path d="M150 130 A 16 16 0 0 1 180 122 L 165 126 Z" fill="#f6d34a" stroke="#d9a90f" strokeWidth="2" />
      {/* chilli */}
      <path d="M62 84 C 70 70, 92 64, 104 70" stroke="var(--chilli)" strokeWidth="7" strokeLinecap="round" fill="none" />
      <path d="M104 70 l 8 -6" stroke="var(--leaf-dark)" strokeWidth="3" strokeLinecap="round" />
    </svg>
  )
}

/** A steaming bowl of rice: the meals page. */
export function RiceBowl({ className }) {
  return (
    <svg className={className} viewBox="0 0 160 140" aria-hidden="true" focusable="false">
      <ellipse cx="80" cy="128" rx="54" ry="7" fill="var(--shadow-ground)" />
      <path d="M58 30 c -6 -8, 6 -14, 0 -22 M80 26 c -6 -8, 6 -14, 0 -22 M102 30 c -6 -8, 6 -14, 0 -22"
        stroke="var(--steam)" strokeWidth="4" strokeLinecap="round" fill="none" />
      <path d="M30 66 C 30 44, 130 44, 130 66 Z" fill="#fffdf6" stroke="var(--rice-line)" strokeWidth="2" />
      <path d="M22 66 H 138 C 138 100, 112 120, 80 120 C 48 120, 22 100, 22 66 Z" fill="var(--leaf)" />
      <path d="M40 84 C 60 92, 100 92, 120 84" stroke="var(--leaf-vein)" strokeWidth="3" fill="none" strokeLinecap="round" />
    </svg>
  )
}

/** A market basket with vegetables: expenses. */
export function Basket({ className }) {
  return (
    <svg className={className} viewBox="0 0 160 140" aria-hidden="true" focusable="false">
      <ellipse cx="80" cy="128" rx="58" ry="7" fill="var(--shadow-ground)" />
      {/* aubergine */}
      <path d="M44 62 C 30 40, 52 24, 66 40 C 72 48, 70 58, 66 64 Z" fill="var(--plum)" />
      <path d="M58 30 l 6 -10 l 6 8 Z" fill="var(--leaf-dark)" />
      {/* carrot */}
      <path d="M86 64 L 112 22 L 98 66 Z" fill="var(--carrot)" />
      <path d="M112 22 l -2 -12 M112 22 l 10 -8 M112 22 l 12 2" stroke="var(--leaf)" strokeWidth="4" strokeLinecap="round" />
      {/* greens */}
      <path d="M70 62 C 64 34, 84 26, 84 62 Z" fill="var(--leaf)" />
      {/* basket */}
      <path d="M24 60 H 136 L 124 118 H 36 Z" fill="var(--basket)" />
      <path d="M30 78 H 130 M33 96 H 127" stroke="var(--basket-dark)" strokeWidth="3" />
      <path d="M56 60 V 118 M80 60 V 118 M104 60 V 118" stroke="var(--basket-dark)" strokeWidth="3" />
      <path d="M40 60 C 40 14, 120 14, 120 60" stroke="var(--basket-dark)" strokeWidth="5" fill="none" />
    </svg>
  )
}

/** Stacked coins and a taka note: payments. */
export function Coins({ className }) {
  return (
    <svg className={className} viewBox="0 0 160 140" aria-hidden="true" focusable="false">
      <ellipse cx="80" cy="128" rx="58" ry="7" fill="var(--shadow-ground)" />
      <rect x="18" y="44" width="92" height="52" rx="6" fill="var(--leaf)" transform="rotate(-10 64 70)" />
      <circle cx="62" cy="72" r="13" fill="none" stroke="#fff" strokeWidth="3" transform="rotate(-10 64 70)" />
      <text x="56" y="79" fontSize="18" fontWeight="700" fill="#fff" transform="rotate(-10 64 70)">৳</text>
      {[0, 1, 2, 3].map((i) => (
        <g key={i}>
          <ellipse cx="112" cy={112 - i * 12} rx="28" ry="9" fill="var(--turmeric-dark)" />
          <ellipse cx="112" cy={108 - i * 12} rx="28" ry="9" fill="var(--turmeric)" />
        </g>
      ))}
      <text x="105" y="77" fontSize="13" fontWeight="700" fill="var(--turmeric-dark)">৳</text>
    </svg>
  )
}

/** A clay coin pot (matir bank): the flat's cash. */
export function ClayPot({ className }) {
  return (
    <svg className={className} viewBox="0 0 160 140" aria-hidden="true" focusable="false">
      <ellipse cx="80" cy="128" rx="50" ry="7" fill="var(--shadow-ground)" />
      <path d="M50 38 C 20 52, 18 112, 80 120 C 142 112, 140 52, 110 38 Z" fill="var(--clay)" />
      <path d="M50 38 H 110 L 104 26 H 56 Z" fill="var(--clay-dark)" />
      <rect x="68" y="30" width="24" height="5" rx="2.5" fill="#3b2116" />
      <path d="M38 76 C 60 86, 100 86, 122 76" stroke="var(--clay-dark)" strokeWidth="3" fill="none" />
      <circle cx="80" cy="16" r="10" fill="var(--turmeric)" stroke="var(--turmeric-dark)" strokeWidth="2" />
      <path d="M58 96 c 4 6, 12 6, 16 0 M86 96 c 4 6, 12 6, 16 0" stroke="var(--clay-dark)" strokeWidth="3" fill="none" strokeLinecap="round" />
    </svg>
  )
}

/** A small leaf, the app's mark. */
export function LeafMark({ className }) {
  return (
    <svg className={className} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="9" fill="var(--pen)" />
      <path d="M8 22 C 8 12, 16 7, 25 8 C 25 18, 18 24, 8 22 Z" fill="#fff" />
      <path d="M9 22 C 13 18, 17 14, 22 11" stroke="var(--pen)" strokeWidth="1.8" fill="none" strokeLinecap="round" />
    </svg>
  )
}

/* ---------------------------------------------------------- line icons */

const iconProps = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
  focusable: false,
}

export const Icons = {
  overview: (p) => (
    <svg {...iconProps} {...p}>
      <rect x="3" y="3" width="7.5" height="9" rx="2" />
      <rect x="13.5" y="3" width="7.5" height="5" rx="2" />
      <rect x="13.5" y="11" width="7.5" height="10" rx="2" />
      <rect x="3" y="15" width="7.5" height="6" rx="2" />
    </svg>
  ),
  meals: (p) => (
    <svg {...iconProps} {...p}>
      <path d="M3 11h18c0 5-4 9-9 9s-9-4-9-9z" />
      <path d="M8 7c-1-1 1-2 0-3M12 7c-1-1 1-2 0-3M16 7c-1-1 1-2 0-3" />
    </svg>
  ),
  expenses: (p) => (
    <svg {...iconProps} {...p}>
      <path d="M3 10h18l-2 10H5z" />
      <path d="M7 10c0-4 2.5-6 5-6s5 2 5 6" />
      <path d="M9 14v3M12 14v3M15 14v3" />
    </svg>
  ),
  payments: (p) => (
    <svg {...iconProps} {...p}>
      <ellipse cx="9" cy="7" rx="6" ry="3" />
      <path d="M3 7v5c0 1.7 2.7 3 6 3s6-1.3 6-3V7" />
      <path d="M9 15v2c0 1.7 2.7 3 6 3s6-1.3 6-3v-5c0-1.7-2.7-3-6-3" />
    </svg>
  ),
  cash: (p) => (
    <svg {...iconProps} {...p}>
      <path d="M8 6c-4 2-4.5 9 4 10.5C20.5 15 20 8 16 6z" transform="translate(0 2)" />
      <path d="M8 6h8l-1-2H9z" transform="translate(0 2)" />
      <path d="M11 6.5h2" transform="translate(0 2)" />
    </svg>
  ),
  members: (p) => (
    <svg {...iconProps} {...p}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3 19c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5" />
      <circle cx="17" cy="9" r="2.4" />
      <path d="M16 13.6c2.7.1 5 1.9 5 5" />
    </svg>
  ),
  account: (p) => (
    <svg {...iconProps} {...p}>
      <path d="M6 3h9l4 4v14H6z" />
      <path d="M15 3v4h4M9 11h7M9 15h7M9 19h4" />
    </svg>
  ),
  rate: (p) => (
    <svg {...iconProps} {...p}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M9.5 8.5h4a2 2 0 0 1 0 4h-4h5M9.5 8.5v7" />
    </svg>
  ),
}
