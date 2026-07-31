export const tokens = {
  color: {
    anchor: '#6F3FF5',
    anchorSoft: '#9B7BFF',
    ink: '#0B0B0E',
    surface: '#FAFAFB',
    surfaceAlt: '#F4F2F8',
    border: '#E8E6EF',
    critical: '#D64545',
    ok: '#22C55E',
    warn: '#F59E0B',
    muted: '#71717A',
  },
  font: {
    sans: 'IBM Plex Sans, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    display: 'Sora, IBM Plex Sans, sans-serif',
    mono: 'JetBrains Mono, ui-monospace, SFMono-Regular, monospace',
  },
  radius: { sm: 6, md: 10, lg: 16, xl: 24 },
  space: { 1: 4, 2: 8, 3: 12, 4: 16, 5: 24, 6: 32, 8: 48, 10: 64 },
} as const

type Token = typeof tokens
export type ColorToken = keyof typeof tokens.color
export default tokens as Token

export const sizePx = (n: keyof Token['space']): `${number}px` => `${tokens.space[n]}px`