import {
  doc,
  getDoc,
  collection,
  query,
  where,
  getDocs,
  writeBatch,
  onSnapshot,
  updateDoc,
  deleteDoc,
  documentId,
  arrayUnion,
  arrayRemove,
  limit,
  type DocumentReference,
  type Unsubscribe,
} from 'firebase/firestore'
import { db } from '../firebase'
import type { Trip, TripMember } from '../types/models'
import { deriveStatus, generateJoinCode } from '../lib/tripUtils'
import { bgForSeed, letterFromName } from '../lib/avatarIdentity'
import { fetchUsersByIds, getUser, removeTripFromUser } from './userService'

export function tripDocRef(tripId: string) {
  return doc(db, 'trips', tripId)
}

export function parseTrip(id: string, data: Record<string, unknown> | undefined): Trip | null {
  if (!data) return null
  const memberIdsRaw = data.memberIds
  const memberIds = Array.isArray(memberIdsRaw)
    ? memberIdsRaw.map((x) => String(x))
    : []
  return {
    id,
    name: String(data.name ?? ''),
    destination: String(data.destination ?? ''),
    description: String(data.description ?? ''),
    startDate: Number(data.startDate ?? 0),
    endDate: Number(data.endDate ?? 0),
    budget: Number(data.budget ?? 0),
    adminId: String(data.adminId ?? ''),
    joinCode: String(data.joinCode ?? ''),
    memberIds,
    status: String(data.status ?? 'upcoming'),
  }
}

export function parseTripMember(
  id: string,
  data: Record<string, unknown> | undefined,
): TripMember | null {
  if (!data) return null
  const adminRaw = data.isAdmin ?? data.admin
  const isAdmin = adminRaw === true
  return {
    userId: id,
    name: String(data.name ?? ''),
    email: String(data.email ?? ''),
    photoUrl: data.photoUrl != null ? String(data.photoUrl) : null,
    isAdmin,
    avatarLetter: data.avatarLetter != null ? String(data.avatarLetter) : null,
    avatarColorHex: data.avatarColorHex != null ? String(data.avatarColorHex) : null,
  }
}

export async function fetchTripsForUser(tripIds: string[]): Promise<Trip[]> {
  if (!tripIds.length) return []
  const tripsCol = collection(db, 'trips')
  const out: Trip[] = []
  for (let i = 0; i < tripIds.length; i += 10) {
    const chunk = tripIds.slice(i, i + 10)
    const q = query(tripsCol, where(documentId(), 'in', chunk))
    const snap = await getDocs(q)
    snap.forEach((d) => {
      const t = parseTrip(d.id, d.data() as Record<string, unknown>)
      if (t) out.push(t)
    })
  }
  const rank = (t: Trip) => {
    const s = deriveStatus(t.startDate, t.endDate)
    if (s === 'active') return 0
    if (s === 'upcoming') return 1
    return 2
  }
  return out.sort((a, b) => rank(a) - rank(b))
}

export async function getTrip(tripId: string): Promise<Trip | null> {
  const s = await getDoc(tripDocRef(tripId))
  if (!s.exists()) return null
  return parseTrip(s.id, s.data() as Record<string, unknown>)
}

export async function canUserManageTripAsLeader(tripId: string, uid: string): Promise<boolean> {
  if (!uid) return false
  const trip = await getTrip(tripId)
  if (!trip) return false
  if (trip.adminId === uid) return true
  const mem = await getDoc(doc(db, 'trips', tripId, 'members', uid))
  if (!mem.exists()) return false
  const m = parseTripMember(mem.id, mem.data() as Record<string, unknown>)
  return m?.isAdmin === true
}

export function subscribeTrip(tripId: string, cb: (trip: Trip | null) => void): Unsubscribe {
  return onSnapshot(tripDocRef(tripId), (snap) => {
    cb(snap.exists() ? parseTrip(snap.id, snap.data() as Record<string, unknown>) : null)
  })
}

export async function enrichMembers(members: TripMember[]): Promise<TripMember[]> {
  if (!members.length) return []
  const ids = [...new Set(members.map((m) => m.userId).filter(Boolean))]
  const userById = await fetchUsersByIds(ids)
  const result: TripMember[] = []
  for (const m of members) {
    const uid = m.userId
    let u = userById.get(uid)
    if (!u) {
      u = await getUser(uid) ?? undefined
      if (u) userById.set(uid, u)
    }
    const displayName = u?.name?.trim() || m.name
    const displayEmail = u?.email?.trim() || m.email
    let mergedPhoto: string | null = null
    if (u != null) {
      mergedPhoto = u.photoUrl?.trim() ? u.photoUrl : null
    } else if (m.photoUrl?.trim()) {
      mergedPhoto = m.photoUrl
    }
    const noPhoto = !mergedPhoto?.trim()
    let letL: string
    let bg: string
    if (noPhoto && u != null) {
      letL = letterFromName(displayName)
      bg = bgForSeed(uid)
    } else if (noPhoto) {
      letL = letterFromName(displayName)
      bg = bgForSeed(uid)
    } else {
      letL = u?.avatarLetter?.trim() || letterFromName(displayName)
      bg = u?.avatarColorHex?.trim() || bgForSeed(uid)
    }
    result.push({
      ...m,
      name: displayName,
      email: displayEmail,
      photoUrl: mergedPhoto,
      avatarLetter: letL,
      avatarColorHex: bg,
    })
  }
  return result
}

export function subscribeTripMembers(
  tripId: string,
  cb: (members: TripMember[]) => void,
): Unsubscribe {
  return onSnapshot(collection(db, 'trips', tripId, 'members'), async (snap) => {
    const list: TripMember[] = []
    snap.forEach((d) => {
      const m = parseTripMember(d.id, d.data() as Record<string, unknown>)
      if (m) list.push(m)
    })
    try {
      const enriched = await enrichMembers(list)
      cb(enriched)
    } catch {
      cb(list)
    }
  })
}

export async function createTrip(trip: Trip, adminMember: TripMember): Promise<string> {
  const ref = doc(collection(db, 'trips'))
  const joinCode = generateJoinCode()
  const status = deriveStatus(trip.startDate, trip.endDate)
  const newTrip = {
    id: ref.id,
    name: trip.name,
    destination: trip.destination,
    description: trip.description,
    startDate: trip.startDate,
    endDate: trip.endDate,
    budget: trip.budget,
    adminId: trip.adminId,
    joinCode,
    memberIds: [adminMember.userId],
    status,
  }
  const batch = writeBatch(db)
  batch.set(ref, newTrip)
  batch.set(doc(db, 'trips', ref.id, 'members', adminMember.userId), {
    userId: adminMember.userId,
    name: adminMember.name,
    email: adminMember.email,
    photoUrl: adminMember.photoUrl ?? '',
    isAdmin: true,
    avatarLetter: adminMember.avatarLetter ?? letterFromName(adminMember.name),
    avatarColorHex: adminMember.avatarColorHex ?? bgForSeed(adminMember.userId),
  })
  await batch.commit()
  return ref.id
}

export async function joinTrip(joinCode: string, member: TripMember): Promise<string | null> {
  const code = joinCode.trim().toUpperCase()
  const q = query(collection(db, 'trips'), where('joinCode', '==', code), limit(1))
  const snap = await getDocs(q)
  if (snap.empty) return null
  const tripDoc = snap.docs[0]!
  const tripId = tripDoc.id
  const ref = tripDoc.ref
  const batch = writeBatch(db)
  batch.set(doc(db, 'trips', tripId, 'members', member.userId), {
    userId: member.userId,
    name: member.name,
    email: member.email,
    photoUrl: member.photoUrl ?? '',
    isAdmin: false,
    avatarLetter: member.avatarLetter ?? letterFromName(member.name),
    avatarColorHex: member.avatarColorHex ?? bgForSeed(member.userId),
  })
  batch.update(ref, { memberIds: arrayUnion(member.userId) })
  await batch.commit()
  return tripId
}

export async function updateTripDetails(
  tripId: string,
  fields: {
    name: string
    destination: string
    description: string
    startDate: number
    endDate: number
    budget: number
  },
): Promise<void> {
  const status = deriveStatus(fields.startDate, fields.endDate)
  await updateDoc(tripDocRef(tripId), {
    name: fields.name,
    destination: fields.destination,
    description: fields.description,
    startDate: fields.startDate,
    endDate: fields.endDate,
    budget: fields.budget,
    status,
  })
}

export async function setMemberAdminRole(
  tripId: string,
  targetUserId: string,
  asAdmin: boolean,
  actingUserId: string,
  tripCreatorId: string,
): Promise<void> {
  if (actingUserId !== tripCreatorId) {
    throw new Error('Only the trip organiser can change member roles')
  }
  if (targetUserId === tripCreatorId) {
    throw new Error('The organiser cannot be demoted')
  }
  await updateDoc(doc(db, 'trips', tripId, 'members', targetUserId), {
    isAdmin: asAdmin,
    admin: asAdmin,
  })
}

export async function removeMemberFromTrip(
  tripId: string,
  targetUserId: string,
  actingOrganiserId: string,
  tripOrganiserId: string,
): Promise<void> {
  if (actingOrganiserId !== tripOrganiserId) {
    throw new Error('Only the trip organiser can remove members')
  }
  if (targetUserId === tripOrganiserId) {
    throw new Error('Cannot remove the organiser')
  }
  const batch = writeBatch(db)
  batch.delete(doc(db, 'trips', tripId, 'members', targetUserId))
  batch.update(tripDocRef(tripId), { memberIds: arrayRemove(targetUserId) })
  await batch.commit()
  await removeTripFromUser(targetUserId, tripId)
}

function chunkIds<T>(arr: T[], size: number): T[][] {
  const out: T[][] = []
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size))
  return out
}

async function deleteDocRefs(refs: DocumentReference[]): Promise<void> {
  for (const group of chunkIds(refs, 400)) {
    const batch = writeBatch(db)
    for (const r of group) batch.delete(r)
    await batch.commit()
  }
}

/** Mirrors `TripRepository.deleteTripAsAdmin` — caller must verify leader permission. */
export async function deleteTripAsLeader(tripId: string, organiserUid: string): Promise<void> {
  const tripRef = tripDocRef(tripId)

  async function wipeSubcollection(name: string) {
    const snap = await getDocs(collection(db, 'trips', tripId, name))
    await deleteDocRefs(snap.docs.map((d) => d.ref))
  }

  await wipeSubcollection('expenses')
  await wipeSubcollection('tasks')

  const memberSnap = await getDocs(collection(db, 'trips', tripId, 'members'))
  const nonOrganiser = memberSnap.docs.filter((d) => d.id !== organiserUid)
  await deleteDocRefs(nonOrganiser.map((d) => d.ref))

  await deleteDoc(tripRef)
  await deleteDoc(doc(db, 'trips', tripId, 'members', organiserUid))
}

export async function deleteTripForCurrentUser(
  tripId: string,
  uid: string,
  organiserUid: string,
): Promise<void> {
  if (uid !== organiserUid) {
    throw new Error('Only the trip organiser can delete this trip')
  }
  await deleteTripAsLeader(tripId, organiserUid)
  await removeTripFromUser(uid, tripId)
}

export type LeaveTripResult =
  | { ok: true }
  | { ok: false; reason: 'last_member' }
  | { ok: false; reason: 'need_transfer'; candidates: TripMember[] }

/**
 * Leave a trip (member removes themselves). If the user is the only admin left, a new organiser must be chosen.
 */
export async function leaveTripAsMember(
  tripId: string,
  uid: string,
  trip: Trip,
  members: TripMember[],
  transferOrganiserToUserId?: string,
): Promise<LeaveTripResult> {
  if (members.length <= 1) {
    return { ok: false, reason: 'last_member' }
  }
  const me = members.find((m) => m.userId === uid)
  if (!me) {
    throw new Error('Not a member of this trip')
  }

  const otherAdmins = members.filter((m) => m.isAdmin && m.userId !== uid)
  const elevated = me.isAdmin || trip.adminId === uid
  if (elevated && otherAdmins.length === 0) {
    const candidates = members.filter((m) => m.userId !== uid)
    if (candidates.length === 0) return { ok: false, reason: 'last_member' }
    if (!transferOrganiserToUserId) {
      return { ok: false, reason: 'need_transfer', candidates }
    }
    await updateDoc(tripDocRef(tripId), { adminId: transferOrganiserToUserId })
    await updateDoc(doc(db, 'trips', tripId, 'members', transferOrganiserToUserId), {
      isAdmin: true,
    })
  }

  const tripRef = tripDocRef(tripId)
  const batch = writeBatch(db)
  batch.delete(doc(db, 'trips', tripId, 'members', uid))
  batch.update(tripRef, { memberIds: arrayRemove(uid) })
  await batch.commit()
  await removeTripFromUser(uid, tripId)
  return { ok: true }
}
