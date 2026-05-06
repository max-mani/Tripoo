import {
  collection,
  doc,
  getDoc,
  onSnapshot,
  orderBy,
  query,
  updateDoc,
  deleteDoc,
  setDoc,
  type Unsubscribe,
} from 'firebase/firestore'
import { db } from '../firebase'
import type { Expense } from '../types/models'

export function parseExpense(id: string, data: Record<string, unknown> | undefined): Expense | null {
  if (!data) return null
  return {
    id,
    title: String(data.title ?? ''),
    amount: Number(data.amount ?? 0),
    category: String(data.category ?? 'other'),
    paidBy: String(data.paidBy ?? ''),
    splitWith: Array.isArray(data.splitWith)
      ? data.splitWith.map((x) => String(x))
      : [],
    timestamp: Number(data.timestamp ?? Date.now()),
    settled: data.settled === true,
  }
}

export function subscribeExpenses(tripId: string, cb: (rows: Expense[]) => void): Unsubscribe {
  const q = query(
    collection(db, 'trips', tripId, 'expenses'),
    orderBy('timestamp', 'desc'),
  )
  return onSnapshot(
    q,
    (snap) => {
      const list: Expense[] = []
      snap.forEach((d) => {
        const e = parseExpense(d.id, d.data() as Record<string, unknown>)
        if (e) list.push(e)
      })
      cb(list)
    },
    () => cb([]),
  )
}

export async function addExpense(tripId: string, expense: Omit<Expense, 'id'>): Promise<string> {
  const ref = doc(collection(db, 'trips', tripId, 'expenses'))
  await setDoc(ref, {
    title: expense.title,
    amount: expense.amount,
    category: expense.category,
    paidBy: expense.paidBy,
    splitWith: expense.splitWith,
    timestamp: expense.timestamp,
    settled: expense.settled,
  })
  return ref.id
}

export async function updateExpense(tripId: string, expense: Expense): Promise<void> {
  const ref = doc(db, 'trips', tripId, 'expenses', expense.id)
  const existing = await getDoc(ref)
  const existingSettled = existing.exists() && existing.get('settled') === true
  const finalSettled = expense.settled || existingSettled
  await updateDoc(ref, {
    title: expense.title,
    amount: expense.amount,
    category: expense.category,
    paidBy: expense.paidBy,
    splitWith: expense.splitWith,
    timestamp: expense.timestamp,
    settled: finalSettled,
  })
}

export async function markExpenseSettled(
  tripId: string,
  expenseId: string,
  settled: boolean,
): Promise<void> {
  await updateDoc(doc(db, 'trips', tripId, 'expenses', expenseId), { settled })
}

export async function deleteExpense(tripId: string, expenseId: string): Promise<void> {
  await deleteDoc(doc(db, 'trips', tripId, 'expenses', expenseId))
}
