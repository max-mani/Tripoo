import { fetchTripsForUser } from './tripService'
import { getTotalExpenses } from './expenseService'
import { formatInrCompact, formatInrFull } from '../lib/inrFormat'

export type ProfileStats = {
  tripCount: number
  activeTripCount: number
  friendsUnique: number
  spentCompact: string
  spentFullInr: string
}

export async function loadProfileStats(uid: string, tripIds: string[]): Promise<ProfileStats> {
  if (!tripIds.length) {
    return {
      tripCount: 0,
      activeTripCount: 0,
      friendsUnique: 0,
      spentCompact: '₹0',
      spentFullInr: '₹0',
    }
  }
  const trips = await fetchTripsForUser(tripIds)
  const activeTripCount = trips.filter((t) => t.status === 'active').length
  const others = new Set<string>()
  let spent = 0
  for (const t of trips) {
    spent += await getTotalExpenses(t.id)
    for (const mid of t.memberIds) {
      if (mid !== uid) others.add(mid)
    }
  }
  return {
    tripCount: trips.length,
    activeTripCount,
    friendsUnique: others.size,
    spentCompact: formatInrCompact(spent),
    spentFullInr: formatInrFull(spent),
  }
}
