import type { Expense } from '../types/models'

/** Matches ExpensesViewModel.processExpenses (unsettled only). */
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
  return computeYouOweYouAreOwed(
    list.filter((e) => !e.settled),
    currentUserId,
  )
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
