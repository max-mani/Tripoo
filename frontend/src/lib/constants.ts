export const EXPENSE_CATEGORIES = [
  { key: 'accommodation', label: 'Accommodation', tint: '#2563EB', bg: '#DBEAFE' },
  { key: 'food', label: 'Food', tint: '#EA580C', bg: '#FFEDD5' },
  { key: 'transport', label: 'Transport', tint: '#9333EA', bg: '#F3E8FF' },
  { key: 'drinks', label: 'Drinks', tint: '#16A34A', bg: '#DCFCE7' },
  { key: 'activities', label: 'Activities', tint: '#CA8A04', bg: '#FEF9C3' },
  { key: 'other', label: 'Other', tint: '#6B7280', bg: '#F3F4F6' },
] as const

export function categoryMeta(key: string) {
  return EXPENSE_CATEGORIES.find((c) => c.key === key) ?? EXPENSE_CATEGORIES[5]!
}

/** Keys align with Android `AddTaskBottomSheet` / `TaskAdapter` (`bookings`, not `booking`). */
export const TASK_CATEGORIES = [
  { key: 'general', label: 'General' },
  { key: 'bookings', label: 'Bookings' },
  { key: 'packing', label: 'Packing' },
  { key: 'documents', label: 'Documents' },
  { key: 'other', label: 'Other' },
] as const

export const TASK_PRIORITIES = ['low', 'medium', 'high'] as const
