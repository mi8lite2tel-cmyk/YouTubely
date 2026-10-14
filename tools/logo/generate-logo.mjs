// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Generates docs/assets/logo.svg (full colour) and docs/assets/logo-mark.svg (one colour).
// The mark is the letter Y on a violet cookie-shaped badge. Nothing here resembles the logo or
// colours of YouTube: the palette comes from the app's seed colour #7C4DFF.
//
// Usage: node tools/logo/generate-logo.mjs
import { writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const outDir = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'docs', 'assets')
const f = (n) => Number(n.toFixed(2))

/** A rounded "cookie" with [lobes] soft bumps, like Material 3 Expressive's Cookie shape. */
function cookie(cx, cy, radius, lobes, depth, steps = 360) {
  const points = []
  for (let i = 0; i < steps; i++) {
    const t = (i / steps) * Math.PI * 2 - Math.PI / 2
    const r = radius * (1 + depth * Math.cos(lobes * (t + Math.PI / 2)))
    points.push(`${f(cx + r * Math.cos(t))},${f(cy + r * Math.sin(t))}`)
  }
  return `M${points.join('L')}Z`
}

/** A four-pointed sparkle centred on (x, y). */
function sparkle(x, y, size) {
  const k = size * 0.22
  return (
    `M${x},${y - size}C${x + k},${y - k} ${x + k},${y - k} ${x + size},${y}` +
    `C${x + k},${y + k} ${x + k},${y + k} ${x},${y + size}` +
    `C${x - k},${y + k} ${x - k},${y + k} ${x - size},${y}` +
    `C${x - k},${y - k} ${x - k},${y - k} ${x},${y - size}Z`
  )
}

const shape = cookie(256, 256, 216, 9, 0.026)

// The Y: two arms meeting in a fork, one stem.
const arms = { left: [142, 150], right: [370, 150], fork: [256, 282], foot: [256, 376] }
const stroke = 46
const yPath =
  `M${arms.left.join(',')}L${arms.fork.join(',')}L${arms.right.join(',')}` +
  `M${arms.fork.join(',')}L${arms.foot.join(',')}`

const full = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512" role="img" aria-labelledby="t d">
  <title id="t">YouTubely</title>
  <desc id="d">The letter Y on a violet cookie-shaped badge.</desc>
  <defs>
    <linearGradient id="body" x1="70" y1="40" x2="440" y2="480" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#B9A3FF"/>
      <stop offset="0.38" stop-color="#8F68FF"/>
      <stop offset="0.72" stop-color="#6A3DEB"/>
      <stop offset="1" stop-color="#4624B8"/>
    </linearGradient>
    <linearGradient id="depth" x1="256" y1="40" x2="256" y2="500" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#4A27C2"/>
      <stop offset="1" stop-color="#2A1180"/>
    </linearGradient>
    <radialGradient id="light" cx="0.28" cy="0.2" r="0.75">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.55"/>
      <stop offset="0.45" stop-color="#FFFFFF" stop-opacity="0.12"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="glow" cx="0.5" cy="0.46" r="0.5">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.26"/>
      <stop offset="0.7" stop-color="#FFFFFF" stop-opacity="0.06"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="rim" x1="90" y1="50" x2="420" y2="470" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.85"/>
      <stop offset="0.5" stop-color="#FFFFFF" stop-opacity="0.12"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0.35"/>
    </linearGradient>
    <linearGradient id="letter" x1="256" y1="130" x2="256" y2="400" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#FFFFFF"/>
      <stop offset="0.6" stop-color="#F1EAFF"/>
      <stop offset="1" stop-color="#D2C2FF"/>
    </linearGradient>
    <clipPath id="clip"><path d="${shape}"/></clipPath>
    <filter id="blur" x="-20%" y="-20%" width="140%" height="150%"><feGaussianBlur stdDeviation="14"/></filter>
    <filter id="soft" x="-20%" y="-20%" width="140%" height="150%"><feGaussianBlur stdDeviation="7"/></filter>
  </defs>

  <!-- Ground shadow, then the darker back layer that gives the badge its thickness. -->
  <path d="${shape}" transform="translate(0 20)" fill="#1B0A55" opacity="0.5" filter="url(#blur)"/>
  <path d="${shape}" transform="translate(0 9)" fill="url(#depth)"/>

  <!-- Badge. -->
  <path d="${shape}" fill="url(#body)"/>
  <g clip-path="url(#clip)">
    <path d="${shape}" fill="url(#light)"/>
    <ellipse cx="256" cy="236" rx="168" ry="164" fill="url(#glow)"/>
    <!-- Two quiet rings echo a dial and a progress arc. -->
    <circle cx="256" cy="262" r="176" fill="none" stroke="#FFFFFF" stroke-opacity="0.10" stroke-width="2"/>
    <path d="M84,262A172,172 0 0 1 214,95" fill="none" stroke="#FFFFFF" stroke-opacity="0.55" stroke-width="5" stroke-linecap="round"/>
    <circle cx="256" cy="262" r="136" fill="none" stroke="#FFFFFF" stroke-opacity="0.07" stroke-width="2"/>
  </g>
  <path d="${shape}" fill="none" stroke="url(#rim)" stroke-width="3.5"/>

  <!-- The Y, with a soft shadow underneath. -->
  <g transform="translate(0 12)" opacity="0.38" filter="url(#soft)" fill="none" stroke="#1B0A55" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round">
    <path d="${yPath}"/>
  </g>
  <path d="${yPath}" fill="none" stroke="url(#letter)" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- A thin light edge along the top of the left arm and the stem. -->
  <path d="M${arms.left[0] - 6},${arms.left[1] - 18}L${arms.fork[0] - 6},${arms.fork[1] - 14}" fill="none" stroke="#FFFFFF" stroke-opacity="0.85" stroke-width="4" stroke-linecap="round"/>


  <!-- Sparkles. -->
  <path d="${sparkle(402, 112, 22)}" fill="#FFFFFF" opacity="0.95"/>
  <path d="${sparkle(436, 158, 10)}" fill="#FFFFFF" opacity="0.7"/>
  <path d="${sparkle(110, 396, 9)}" fill="#FFFFFF" opacity="0.5"/>
</svg>
`

const mark = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512" role="img" aria-labelledby="t">
  <title id="t">YouTubely</title>
  <!-- One colour (currentColor): for themed icons, stamps and single-colour print. -->
  <g fill="none" stroke="currentColor" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round">
    <path d="${yPath}"/>
  </g>
</svg>
`

writeFileSync(join(outDir, 'logo.svg'), full)
writeFileSync(join(outDir, 'logo-mark.svg'), mark)
console.log('Wrote docs/assets/logo.svg and docs/assets/logo-mark.svg')
