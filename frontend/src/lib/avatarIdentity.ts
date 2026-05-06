/** Mirrors com.manikandan.tripoo.utils.UserAvatarIdentity */

export const AVATAR_BG_HEX: string[] = [
  '#FFEDD5',
  '#DCFCE7',
  '#DBEAFE',
  '#F3E8FF',
  '#FEF9C3',
  '#FFE4E6',
  '#E0F2FE',
  '#F5F5F4',
]

export const AVATAR_TEXT_HEX: string[] = [
  '#C05C00',
  '#16A34A',
  '#2563EB',
  '#9333EA',
  '#CA8A04',
  '#BE123C',
  '#0369A1',
  '#57534D',
]

export function letterFromName(name?: string | null): string {
  const t = name?.trim() ?? ''
  if (!t.length) return '?'
  const c = t[0]!.toUpperCase()
  return /[A-Z0-9]/i.test(c) ? c : '?'
}

export function pickPaletteIndex(seed: string): number {
  if (!seed.length) return 0
  let h = 0
  for (let i = 0; i < seed.length; i++) h = (Math.imul(31, h) + seed.charCodeAt(i)) | 0
  return Math.abs(h) % AVATAR_BG_HEX.length
}

export function bgForSeed(seed: string): string {
  return AVATAR_BG_HEX[pickPaletteIndex(seed)]!
}

export function textColorForSeed(seed: string): string {
  return AVATAR_TEXT_HEX[pickPaletteIndex(seed)]!
}
