# Phase 3 — Real Trip Planning Features

## Goal

You said the app's primary identity should be **trip planner first**. Today "planning" is just a countdown timer and a budget bar. This phase adds three genuinely useful planning tools: a day-by-day **itinerary**, **polls** for group decisions, and **shared notes**. All are optional/collapsible so a quick Outing (Phase 1) isn't cluttered with planning tools it doesn't need.

Bottom nav (Home/Expenses/Tasks/Groups) stays untouched — these features live inside the **Home** screen's Quick Access area, not as new nav tabs, to respect "keep the current layout" and avoid crowding a 4-slot nav bar.

## 1. Itinerary

### Data model

`trips/{tripId}/itinerary/{dayId}`:
```kotlin
data class ItineraryDay(
    val id: String = "",
    val dayIndex: Int = 0,          // 0-based, for ordering
    val date: Long? = null,         // optional — outings/undated trips can skip this
    val stops: List<ItineraryStop> = emptyList()
)

data class ItineraryStop(
    val id: String = "",
    val time: String = "",          // free text, e.g. "9:00 AM" — keeps input simple
    val title: String = "",
    val location: String = "",
    val notes: String = "",
    val createdBy: String = ""
)
```
Storing `stops` as an embedded list inside the day document (rather than a further subcollection) keeps reads cheap — a trip realistically has a handful of days with a handful of stops each, well within Firestore's 1MB document limit.

### Firestore rules addition
```
match /itinerary/{dayId} {
  allow read: if isAuthenticated() && request.auth.uid in tripSnap().memberIds;
  allow create, update: if isAuthenticated() && request.auth.uid in tripSnap().memberIds;
  allow delete: if isAuthenticated()
    && (isTripLeaderUid(request.auth.uid) || request.auth.uid in tripSnap().memberIds);
}
```
Any member can propose/edit stops (matches the collaborative spirit of trip planning); tightening this to leader-only is a one-line change later if you find it gets messy in practice.

### UI

- New Quick Access tile on Home: **"Itinerary"** (icon: calendar/route).
- Opens `ItineraryFragment`: horizontal day tabs (Day 1, Day 2, …) each showing a vertical timeline of stops (reuse the visual language of `item_task.xml` rows — time on the left, title/location/notes on the right, small edit/delete menu matching `btnTaskMore`).
- "+" FAB opens a small bottom sheet: time, title, location, notes — same form pattern as `AddTaskBottomSheet`.
- Home screen gets a compact **"Today" card** showing just the next 1–2 upcoming stops for the current day, so you don't have to open the full itinerary to see what's next (only shown for trips with a `date` set — outings and date-less trips skip this card).

### Files to add (Android)

| File | Purpose |
|---|---|
| `data/model/ItineraryDay.kt`, `ItineraryStop.kt` | Models above |
| `data/repository/ItineraryRepository.kt` | CRUD + listener, same shape as `TaskRepository.kt` |
| `ui/itinerary/ItineraryViewModel.kt`, `ItineraryFragment.kt`, `AddItineraryStopBottomSheet.kt` | Screen + add/edit sheet |
| `fragment_itinerary.xml`, `item_itinerary_day_tab.xml`, `item_itinerary_stop.xml`, `bottom_sheet_add_itinerary_stop.xml` | Layouts, styled to match existing task/expense rows |
| `HomeFragment.kt` / `fragment_home.xml` | Add Quick Access tile + optional "Today" card |

## 2. Polls (group decisions)

### Data model

`trips/{tripId}/polls/{pollId}`:
```kotlin
data class Poll(
    val id: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val votes: Map<String, Int> = emptyMap(),   // uid -> option index
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val closed: Boolean = false
)
```

### Firestore rules addition
```
match /polls/{pollId} {
  allow read: if isAuthenticated() && request.auth.uid in tripSnap().memberIds;
  allow create: if isAuthenticated()
    && request.auth.uid in tripSnap().memberIds
    && request.resource.data.createdBy == request.auth.uid;
  allow delete: if isAuthenticated()
    && (isTripLeaderUid(request.auth.uid) || resource.data.createdBy == request.auth.uid);
  // Voting: any member may update, but only their own key in `votes`, and only when not closed
  allow update: if isAuthenticated()
    && request.auth.uid in tripSnap().memberIds
    && (
      (resource.data.get('closed', false) == false
        && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['votes'])
        && request.resource.data.votes.diff(resource.data.votes).affectedKeys().hasOnly([request.auth.uid]))
      || (isTripLeaderUid(request.auth.uid)
          && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['closed']))
    );
}
```
This lets any member cast/change their own vote, but not touch anyone else's vote or the poll itself once closed; only a leader can close a poll.

### UI

- New Quick Access tile: **"Polls"**.
- Home shows an **"Active poll" card** if any open poll exists (question + live vote count + your current pick highlighted) — tapping opens the full poll detail.
- Full `PollsFragment`: list of polls (active first, then closed/history), each row shows question + leading option + vote count.
- Poll detail: question, tappable option rows (each shows a small progress bar of vote share, tapping casts/changes your vote), "Close poll" button for leaders.
- Create poll: small bottom sheet — question text field + dynamic list of option text fields (start with 2, "+ add option" button, matches the simple add-field pattern already used nowhere else in the app but is a standard small component).

### Files to add (Android)

| File | Purpose |
|---|---|
| `data/model/Poll.kt` | Model above |
| `data/repository/PollRepository.kt` | CRUD + vote(pollId, optionIndex) |
| `ui/polls/PollsViewModel.kt`, `PollsFragment.kt`, `PollDetailFragment.kt`, `CreatePollBottomSheet.kt` | Screens |
| Layouts mirroring `fragment_tasks.xml` list style | — |
| `HomeFragment.kt` | Active-poll card + Quick Access tile |

## 3. Shared Notes

The simplest of the three — a single free-text scratchpad per trip for reservation numbers, packing reminders, links, etc.

### Data model

Reuse the existing `meta` subcollection pattern already used for `expense_summary`:

`trips/{tripId}/meta/notes`:
```
{ "text": "...", "updatedBy": "uid", "updatedAt": 172839...}
```

### Firestore rules

Already covered — `firestore.rules` has a generic `meta/{metaId}` block allowing any member to read/write. No change needed.

### UI

- New Quick Access tile: **"Notes"**.
- Opens a single full-screen text editor (`NotesFragment`) — multi-line `EditText`, autosaves on a short debounce or on back-press, last-edited-by/at shown in a small caption at the bottom.
- No new repository class needed — a couple of methods on `ExpenseRepository`-style pattern, or a tiny new `TripMetaRepository.kt` if you'd rather keep it separate from expenses.

## 4. Home Quick Access reorganization

Current grid (`fragment_home.xml`): Expenses · Tasks · Participants (3 tiles, already duplicated with bottom nav — see Phase 5 for why that's being cleaned up).

New grid for **Trips**: Itinerary · Polls · Notes (the new planning tools — Expenses/Tasks/Participants are dropped from here since bottom nav already covers them, per Phase 5's redundancy fix).

New grid for **Outings**: hidden by default, or collapsed under a "More planning tools" disclosure — outings are usually simple enough not to need this, but nothing stops someone from expanding it if they want a poll for "which restaurant."

## 5. Testing checklist

- [ ] Itinerary stops persist per day, ordering is stable, editing/deleting a stop updates the day's `stops` list correctly (watch for the embedded-list update pattern — always read-modify-write the whole array, same caution as any Firestore array-of-objects field).
- [ ] A poll vote from a non-member is rejected (rules test); a member can change their own vote but not touch another member's vote value.
- [ ] Closing a poll blocks further voting.
- [ ] Notes autosave doesn't fire on every keystroke (debounce, avoid Firestore write storms).
- [ ] Outing Home screen doesn't show the new planning tiles by default.
