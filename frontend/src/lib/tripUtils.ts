import type { Trip } from '../types/models'

export function deriveStatus(startDate: number, endDate: number): string {
  const now = Date.now()
  if (now < startDate) return 'upcoming'
  if (now > endDate) return 'past'
  return 'active'
}

export function sortTripsForDashboard(trips: Trip[]): Trip[] {
  const rank = (s: string) => {
    if (s === 'active') return 0
    if (s === 'upcoming') return 1
    return 2
  }
  return [...trips].sort((a, b) => {
    const sa = deriveStatus(a.startDate, a.endDate)
    const sb = deriveStatus(b.startDate, b.endDate)
    return rank(sa) - rank(sb)
  })
}

export function generateJoinCode(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
  let suffix = ''
  for (let i = 0; i < 3; i++) {
    suffix += chars[Math.floor(Math.random() * chars.length)]
  }
  return `TRP-${suffix}`
}

export function statusLabel(status: string): string {
  switch (status) {
    case 'active':
      return 'Active'
    case 'upcoming':
      return 'Upcoming'
    case 'past':
      return 'Past'
    default:
      return status
  }
}

export function formatTripDates(startMs: number, endMs: number): string {
  const o = { month: 'short', day: 'numeric', year: 'numeric' } as const
  const a = new Date(startMs).toLocaleDateString(undefined, o)
  const b = new Date(endMs).toLocaleDateString(undefined, o)
  return `${a} – ${b}`
}
