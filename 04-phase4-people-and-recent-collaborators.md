# Phase 4 — Smart People Picker ("recently traveled with")

## Goal

You want to keep per-trip membership (join code, no persistent friends list) but have the app **remember who you've traveled with before** and surface them when creating a new trip or outing — so you're not stuck re-sharing a join code with the same five friends every single time.

## 1. The hard constraint (read this first)

Firestore rules currently only allow a user to add **themselves** to a trip's `memberIds`:

```
function isJoiningTrip() {
  return isAuthenticated()
    && ...
    && request.resource.data.memberIds.hasAny([request.auth.uid]);
}
```

This is a deliberate security boundary — an organiser cannot silently write another person into a trip's member list, because that would let anyone add anyone to any trip without consent. **This phase does not change that boundary.** So "recently traveled with" cannot mean "tap a name and they're instantly a member" — it means **"tap a name to instantly notify/share the join code with them"**, which is a UX shortcut, not a permission change.

If you want true one-tap silent adds later, that requires a real pending-invite system — sketched as an optional stretch at the end of this file.

## 2. Data model

### `User` gets a new field

`app/src/main/java/com/manikandan/tripoo/data/model/User.kt`:
```kotlin
data class User(
    // ...existing fields...
    val recentCollaborators: List<RecentCollaborator> = emptyList()
)

data class RecentCollaborator(
    val uid: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val lastSeenAt: Long = 0L
)
```

### Maintaining the list (client-side, no Cloud Functions)

Update `recentCollaborators` on the **current user's own doc** (never on someone else's — respects the same self-write boundary as above) at two points:

1. **On trip/outing creation** (`TripRepository.createTrip`): after creating the trip, merge the *other* members already known (usually none yet, so mostly a no-op here — real population happens at step 2).
2. **On viewing a trip's member list** (`GroupsViewModel.loadTripAndMembers` / `TripRepository.listenToTripMembers` callback): whenever the current user loads a trip's member list, merge every *other* member into their own `recentCollaborators` (dedupe by `uid`, update `lastSeenAt`, cap the list at ~20 entries sorted by `lastSeenAt` descending, dropping the oldest beyond that).

This means: the more trips you're in with someone, the more likely they are to surface — with zero server-side logic, entirely from data the client already has in memory.

New method: `UserRepository.mergeRecentCollaborators(uid: String, newOnes: List<RecentCollaborator>)` — read-modify-write pattern similar to `UserRepository.removeTripFromUser`'s transaction.

## 3. The "Add People" bottom sheet

A new reusable component used in three places:
1. Create Trip / Create Outing flow (Phase 1) — optional step before/after creation.
2. Groups tab → **Invite** button.
3. (Optional) A dedicated "Invite more people" action from Home's Quick Access if you want it discoverable beyond the Groups tab.

### Layout

```
┌───────────────────────────────────┐
│  Add people                    ✕  │
├───────────────────────────────────┤
│  RECENTLY TRAVELED WITH            │
│  ○ Alex        [Share invite]     │
│  ○ Sam         [Share invite]     │
│  ○ Priya       [Share invite]     │
├───────────────────────────────────┤
│  Trip code: TRP-482                │
│  [Copy code]   [Share generic]     │
└───────────────────────────────────┘
```

- Tapping **[Share invite]** next to a recent collaborator opens the Android share sheet (`Intent.ACTION_SEND`) prefilled with a personalized message: `"Hey Alex! Join our trip 'Goa Weekend' on Ulla — code: TRP-482"`. This is a one-tap way to *notify* the right person, even though the actual join still requires their own action.
- The generic **Copy/Share** row (already exists in `GroupsFragment`) stays as-is for anyone not in your recents.
- Recent collaborators who are **already members** of this trip are filtered out of the list (no point suggesting someone already in).

### Files to touch (Android)

| File | Change |
|---|---|
| `data/model/User.kt` | Add `RecentCollaborator` + field |
| `data/repository/UserRepository.kt` | Add `mergeRecentCollaborators(uid, newOnes, callback)` |
| `ui/groups/GroupsViewModel.kt` | After `appendCurrentUserIfMissing`/member load, call the merge (fire-and-forget, don't block UI) |
| New: `ui/people/AddPeopleBottomSheet.kt` + `bottom_sheet_add_people.xml` | The component above |
| `GroupsFragment.java` | `btnInvite` opens `AddPeopleBottomSheet` instead of going straight to the OS share sheet |
| `CreateTripFragment.java` | After successful creation, optionally show `AddPeopleBottomSheet` immediately ("Trip created! Invite people now?") |

## 4. Firestore rules

No rules changes needed — `recentCollaborators` is written only by the owning user to their own `users/{uid}` doc, which the existing rule already permits (`allow update: if isAuthenticated() && request.auth.uid == userId`).

## 5. Optional future stretch: real pending invites

Not built in this phase, but documented since it's the natural next step if the share-shortcut above feels insufficient:

- New top-level collection `tripInvites/{inviteId}`: `{tripId, invitedUid, invitedBy, status: "pending"|"accepted"|"declined", createdAt}`.
- Rule: only `invitedBy` can create (must be a trip leader), only `invitedUid` can update `status`.
- Dashboard gets a "Pending invites" section pulling `tripInvites` where `invitedUid == me && status == "pending"`; accepting runs the exact same join logic as a join code today, just without typing the code.
- This is a bigger, genuinely new feature (new collection, new dashboard section, new notification type) — worth its own phase if you decide you want it after using the share-shortcut version for a while.

## 6. Testing checklist

- [ ] After being a member of Trip A with Alex, your `recentCollaborators` includes Alex.
- [ ] Creating Trip B and opening "Add People" shows Alex in "Recently traveled with".
- [ ] A collaborator already in the current trip does not appear in the suggestion list.
- [ ] The list caps at ~20 and evicts the oldest `lastSeenAt` entries beyond that.
- [ ] Tapping "Share invite" opens the Android share sheet with a correctly prefilled message including the trip name and join code.
- [ ] No rule allows one user to write into another user's `recentCollaborators` or `memberIds`.
