export interface Trip {
  id: string
  name: string
  destination: string
  description: string
  startDate: number
  endDate: number
  budget: number
  adminId: string
  joinCode: string
  memberIds: string[]
  status: string
}

export interface User {
  uid: string
  name: string
  email: string
  phoneNumber?: string | null
  preferredLanguage?: string | null
  preferredCurrency?: string | null
  photoUrl?: string | null
  tripIds: string[]
  lastActiveTripId?: string | null
  avatarLetter?: string | null
  avatarColorHex?: string | null
}

export interface TripMember {
  userId: string
  name: string
  email: string
  photoUrl?: string | null
  isAdmin: boolean
  avatarLetter?: string | null
  avatarColorHex?: string | null
}

export interface Expense {
  id: string
  /** Creator uid (required server-side for new expenses) */
  createdBy?: string
  title: string
  amount: number
  category: string
  paidBy: string
  splitWith: string[]
  timestamp: number
  settled: boolean
}

export interface Task {
  id: string
  /** Creator uid (required server-side for new tasks) */
  createdBy?: string
  title: string
  category: string
  assignedTo: string
  completed: boolean
  dueDate: number | null
  priority: string
  notes?: string | null
  deadlineNotified?: boolean
}

export interface TripWithMeta {
  trip: Trip
  memberCount: number
  totalSpent: number
}
