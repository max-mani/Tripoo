import {
  collection,
  doc,
  getDoc,
  onSnapshot,
  updateDoc,
  deleteDoc,
  setDoc,
  type Unsubscribe,
} from 'firebase/firestore'
import { db } from '../firebase'
import type { Task } from '../types/models'

export function parseTask(id: string, data: Record<string, unknown> | undefined): Task | null {
  if (!data) return null
  const due = data.dueDate
  let dueDate: number | null = null
  if (typeof due === 'number') dueDate = due
  else if (due != null) dueDate = Number(due)
  return {
    id,
    title: String(data.title ?? ''),
    category: String(data.category ?? 'general'),
    assignedTo: String(data.assignedTo ?? 'everyone'),
    completed: data.completed === true,
    dueDate,
    priority: String(data.priority ?? 'medium'),
    notes: data.notes != null ? String(data.notes) : null,
    deadlineNotified: data.deadlineNotified === true,
  }
}

export function subscribeTasks(tripId: string, cb: (rows: Task[]) => void): Unsubscribe {
  return onSnapshot(
    collection(db, 'trips', tripId, 'tasks'),
    (snap) => {
      const list: Task[] = []
      snap.forEach((d) => {
        const t = parseTask(d.id, d.data() as Record<string, unknown>)
        if (t) list.push(t)
      })
      cb(list)
    },
    () => cb([]),
  )
}

export async function addTask(tripId: string, task: Omit<Task, 'id'>): Promise<string> {
  const ref = doc(collection(db, 'trips', tripId, 'tasks'))
  await setDoc(ref, {
    title: task.title,
    category: task.category,
    assignedTo: task.assignedTo,
    completed: task.completed,
    dueDate: task.dueDate,
    priority: task.priority,
    notes: task.notes ?? null,
    deadlineNotified: task.deadlineNotified ?? false,
  })
  return ref.id
}

export async function updateTask(tripId: string, taskId: string, task: Task): Promise<void> {
  const docRef = doc(db, 'trips', tripId, 'tasks', taskId)
  const existing = await getDoc(docRef)
  let prevDue: number | null | undefined
  if (existing.exists()) {
    const d = existing.data()
    if (d && 'dueDate' in d && d.dueDate != null) prevDue = Number(d.dueDate)
  }
  const newDue = task.dueDate
  const dueChanged =
    (prevDue == null && newDue != null) ||
    (prevDue != null && newDue == null) ||
    (prevDue != null && newDue != null && prevDue !== newDue)
  const updates: Record<string, unknown> = {
    title: task.title,
    category: task.category,
    assignedTo: task.assignedTo,
    completed: task.completed,
    dueDate: task.dueDate,
    priority: task.priority,
    notes: task.notes ?? null,
  }
  if (dueChanged) updates.deadlineNotified = false
  await updateDoc(docRef, updates)
}

export async function updateTaskCompletion(
  tripId: string,
  taskId: string,
  completed: boolean,
): Promise<void> {
  await updateDoc(doc(db, 'trips', tripId, 'tasks', taskId), { completed })
}

export async function deleteTask(tripId: string, taskId: string): Promise<void> {
  await deleteDoc(doc(db, 'trips', tripId, 'tasks', taskId))
}
