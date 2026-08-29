import {
  doc,
  getDoc,
  onSnapshot,
  setDoc,
  updateDoc,
  arrayUnion,
  arrayRemove,
  writeBatch,
  query,
  collection,
  where,
  documentId,
  getDocs,
  type Unsubscribe,
} from 'firebase/firestore'
import { db } from '../firebase'
import type { User } from '../types/models'
import { bgForSeed, letterFromName } from '../lib/avatarIdentity'

export function userDocRef(uid: string) {
  return doc(db, 'users', uid)
}

export function parseUser(id: string, data: Record<string, unknown> | undefined): User | null {
  if (!data) return null
  const tripIdsRaw = data.tripIds
  const tripIds = Array.isArray(tripIdsRaw)
    ? tripIdsRaw.map((x) => String(x))
    : []
  return {
    uid: id,
    name: String(data.name ?? ''),
    email: String(data.email ?? ''),
    phoneNumber: data.phoneNumber != null ? String(data.phoneNumber) : null,
    preferredLanguage:
      data.preferredLanguage != null ? String(data.preferredLanguage) : null,
    preferredCurrency:
      data.preferredCurrency != null ? String(data.preferredCurrency) : null,
    photoUrl:
      data.photoUrl != null && String(data.photoUrl).trim() !== ''
        ? String(data.photoUrl)
        : null,
    tripIds,
    lastActiveTripId:
      data.lastActiveTripId != null ? String(data.lastActiveTripId) : null,
    avatarLetter: data.avatarLetter != null ? String(data.avatarLetter) : null,
    avatarColorHex:
      data.avatarColorHex != null ? String(data.avatarColorHex) : null,
    recentCollaborators: parseRecentCollaborators(data.recentCollaborators),
  }
}

function parseRecentCollaborators(raw: unknown): User['recentCollaborators'] {
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const m = item as Record<string, unknown>
      const uid = m.uid != null ? String(m.uid) : ''
      if (!uid) return null
      return {
        uid,
        name: m.name != null ? String(m.name) : '',
        photoUrl: m.photoUrl != null ? String(m.photoUrl) : null,
        lastSeenAt: typeof m.lastSeenAt === 'number' ? m.lastSeenAt : Number(m.lastSeenAt) || 0,
      }
    })
    .filter((x): x is NonNullable<typeof x> => x != null)
}

export async function getUser(uid: string): Promise<User | null> {
  const s = await getDoc(userDocRef(uid))
  if (!s.exists()) return null
  return parseUser(s.id, s.data() as Record<string, unknown>)
}

export function subscribeUser(uid: string, cb: (user: User | null) => void): Unsubscribe {
  return onSnapshot(userDocRef(uid), (snap) => {
    cb(snap.exists() ? parseUser(snap.id, snap.data() as Record<string, unknown>) : null)
  })
}

export async function createOrMergeUser(user: User): Promise<void> {
  const letter = user.avatarLetter?.trim() || letterFromName(user.name)
  const color = user.avatarColorHex?.trim() || bgForSeed(user.uid)
  const payload: Record<string, unknown> = {
    name: user.name,
    email: user.email,
    tripIds: user.tripIds,
    photoUrl: user.photoUrl?.trim() ?? '',
    avatarLetter: letter,
    avatarColorHex: color,
  }
  if (user.phoneNumber != null) payload.phoneNumber = user.phoneNumber
  if (user.preferredLanguage != null) payload.preferredLanguage = user.preferredLanguage
  if (user.preferredCurrency != null) payload.preferredCurrency = user.preferredCurrency
  if (user.lastActiveTripId != null) payload.lastActiveTripId = user.lastActiveTripId
  await setDoc(userDocRef(user.uid), payload, { merge: true })
}

export async function addTripToUser(uid: string, tripId: string): Promise<void> {
  await updateDoc(userDocRef(uid), {
    tripIds: arrayUnion(tripId),
    lastActiveTripId: tripId,
  })
}

export async function removeTripFromUser(uid: string, tripId: string): Promise<void> {
  const u = await getUser(uid)
  const updates: Record<string, unknown> = {
    tripIds: arrayRemove(tripId),
  }
  if (u?.lastActiveTripId === tripId) updates.lastActiveTripId = null
  await updateDoc(userDocRef(uid), updates)
}

export async function setLastActiveTrip(uid: string, tripId: string): Promise<void> {
  await updateDoc(userDocRef(uid), { lastActiveTripId: tripId })
}

export async function updateProfile(
  uid: string,
  name: string,
  photoUrl: string | null,
): Promise<void> {
  const trimmed = name.trim()
  const updates: Record<string, unknown> = {
    name: trimmed,
    photoUrl: photoUrl?.trim() ?? '',
    avatarLetter: letterFromName(trimmed),
  }
  if (!photoUrl?.trim()) {
    const existing = await getUser(uid)
    if (!existing?.avatarColorHex?.trim()) {
      updates.avatarColorHex = bgForSeed(uid)
    }
  }
  await updateDoc(userDocRef(uid), updates)
}

export async function updatePreferences(
  uid: string,
  language: string | null,
  currency: string | null,
): Promise<void> {
  const m: Record<string, string> = {}
  if (language != null && language.trim()) m.preferredLanguage = language.trim()
  if (currency != null && currency.trim()) m.preferredCurrency = currency.trim()
  if (Object.keys(m).length) await updateDoc(userDocRef(uid), m)
}

export async function updateDocumentEmail(uid: string, email: string): Promise<void> {
  await updateDoc(userDocRef(uid), { email: email.trim() })
}

export async function updatePhone(uid: string, phone: string): Promise<void> {
  await updateDoc(userDocRef(uid), { phoneNumber: phone.trim() })
}

/** Merge profile fields into each trip member doc (matches TripRepository.syncMemberProfileFromUser). */
export async function syncMemberProfileFromUser(uid: string): Promise<void> {
  const user = await getUser(uid)
  if (!user) return
  const tripIds = [...new Set(user.tripIds.filter((t) => t))].filter(Boolean)
  if (!tripIds.length) return

  let letter = user.avatarLetter?.trim() ?? ''
  if (!letter) {
    letter = letterFromName(user.name || user.email.split('@')[0] || '?')
  }
  let colorHex = user.avatarColorHex?.trim() ?? ''
  if (!colorHex) colorHex = bgForSeed(uid)

  const payload = {
    name: user.name.trim(),
    email: user.email.trim(),
    photoUrl: user.photoUrl?.trim() ?? '',
    avatarLetter: letter,
    avatarColorHex: colorHex,
  }

  for (let i = 0; i < tripIds.length; i += 400) {
    const chunk = tripIds.slice(i, i + 400)
    const batch = writeBatch(db)
    for (const tripId of chunk) {
      const ref = doc(db, 'trips', tripId, 'members', uid)
      batch.set(ref, payload, { merge: true })
    }
    await batch.commit()
  }
}

export async function fetchUsersByIds(ids: string[]): Promise<Map<string, User>> {
  const map = new Map<string, User>()
  const uniq = [...new Set(ids.filter(Boolean))]
  for (let i = 0; i < uniq.length; i += 10) {
    const chunk = uniq.slice(i, i + 10)
    const q = query(collection(db, 'users'), where(documentId(), 'in', chunk))
    const snap = await getDocs(q)
    snap.forEach((d) => {
      const u = parseUser(d.id, d.data() as Record<string, unknown>)
      if (u) map.set(d.id, u)
    })
  }
  return map
}
