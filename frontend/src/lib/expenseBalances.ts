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
