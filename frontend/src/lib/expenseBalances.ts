import type { Expense, Settlement } from '../types/models'

const EPS = 0.01

export type SuggestedPayment = {
  fromUserId: string
  toUserId: string
  amount: number
}

/** Matches SettlementCalculator.computeNetBalances (all expenses; settled flag ignored). */
export function computeNetBalances(
  expenses: Expense[],
  settlements: Settlement[] = [],
  memberIds: string[] = [],
): Record<string, number> {
  const debt = new Map<string, Map<string, number>>()
  const bump = (from: string, to: string, amount: number) => {
    if (!from || !to) return
    let row = debt.get(from)
    if (!row) {
      row = new Map()
      debt.set(from, row)
    }
    row.set(to, (row.get(to) ?? 0) + amount)
  }

  for (const expense of expenses) {
    const share = expense.amount / Math.max(1, expense.splitWith.length)
    for (const uid of expense.splitWith) {
      if (!uid || uid === expense.paidBy) continue
      bump(uid, expense.paidBy, share)
    }
  }
  for (const s of settlements) {
    bump(s.fromUserId, s.toUserId, -s.amount)
  }

  const ids = new Set<string>()
  for (const id of memberIds) if (id) ids.add(id)
  for (const [from, row] of debt) {
    ids.add(from)
    for (const to of row.keys()) ids.add(to)
  }
  for (const e of expenses) {
    if (e.paidBy) ids.add(e.paidBy)
    for (const u of e.splitWith) if (u) ids.add(u)
  }
  for (const s of settlements) {
    if (s.fromUserId) ids.add(s.fromUserId)
    if (s.toUserId) ids.add(s.toUserId)
  }

  const net: Record<string, number> = {}
  for (const uid of ids) {
    let shouldPay = 0
    const row = debt.get(uid)
    if (row) for (const v of row.values()) shouldPay += v
    let shouldReceive = 0
    for (const row of debt.values()) shouldReceive += row.get(uid) ?? 0
    net[uid] = shouldReceive - shouldPay
  }
  return net
}

/** Matches SettlementCalculator.simplifyDebts. */
export function simplifyDebts(net: Record<string, number>): SuggestedPayment[] {
  const debtors: { id: string; amt: number }[] = []
  const creditors: { id: string; amt: number }[] = []
  for (const [id, value] of Object.entries(net)) {
    if (value < -EPS) debtors.push({ id, amt: -value })
    else if (value > EPS) creditors.push({ id, amt: value })
  }
  const result: SuggestedPayment[] = []
  while (debtors.length && creditors.length) {
    debtors.sort((a, b) => b.amt - a.amt)
    creditors.sort((a, b) => b.amt - a.amt)
    const d = debtors.shift()!
    const c = creditors.shift()!
    const pay = Math.min(d.amt, c.amt)
    if (pay >= EPS) {
      result.push({ fromUserId: d.id, toUserId: c.id, amount: Math.round(pay * 100) / 100 })
    }
    const dLeft = d.amt - pay
    const cLeft = c.amt - pay
    if (dLeft > EPS) debtors.push({ id: d.id, amt: dLeft })
    if (cLeft > EPS) creditors.push({ id: c.id, amt: cLeft })
  }
  return result
}

/** Header chips from net (matches ExpensesViewModel after Phase 2). */
export function youOweYouAreOwedFromNet(
  net: Record<string, number>,
  currentUserId: string,
): { youOwe: number; youAreOwed: number } {
  const my = net[currentUserId] ?? 0
  return {
    youOwe: my < 0 ? -my : 0,
    youAreOwed: my > 0 ? my : 0,
  }
}

/** Matches ExpensesViewModel.processExpenses (unsettled only). Kept for existing web UI. */
export function computeYouOweYouAreOwed(
  expenses: Expense[],
  currentUserId: string,
): { youOwe: number; youAreOwed: number } {
  let youOwe = 0
  let youAreOwed = 0
  for (const expense of expenses) {
    if (expense.settled) continue
    const share = expense.amount / Math.max(1, expense.splitWith.length)
    if (expense.paidBy !== currentUserId && expense.splitWith.includes(currentUserId)) {
      youOwe += share
    } else if (expense.paidBy === currentUserId) {
      youAreOwed += expense.splitWith.filter((id) => id !== currentUserId).length * share
    }
  }
  return { youOwe, youAreOwed }
}

function percentDelta(current: number, previous: number): number {
  if (previous <= 0) return current > 0 ? 100 : 0
  return Math.round(((current - previous) / previous) * 100)
}

function weekBalances(list: Expense[], currentUserId: string) {
  return computeYouOweYouAreOwed(list, currentUserId)
}

/** Week-over-week % change for owe / owed chips (ExpensesViewModel.computeWeeklyTrend). */
export function computeOweOwedTrends(expenses: Expense[], currentUserId: string) {
  const now = Date.now()
  const weekMs = 7 * 24 * 60 * 60 * 1000
  const currentWindowStart = now - weekMs
  const previousWindowStart = currentWindowStart - weekMs
  const currentWeek = expenses.filter((e) => e.timestamp >= currentWindowStart && e.timestamp <= now)
  const previousWeek = expenses.filter(
    (e) => e.timestamp >= previousWindowStart && e.timestamp < currentWindowStart,
  )
  const cur = weekBalances(currentWeek, currentUserId)
  const prev = weekBalances(previousWeek, currentUserId)
  return {
    oweTrendPct: percentDelta(cur.youOwe, prev.youOwe),
    owedTrendPct: percentDelta(cur.youAreOwed, prev.youAreOwed),
  }
}
