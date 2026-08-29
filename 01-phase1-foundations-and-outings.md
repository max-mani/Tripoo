# Phase 1 — Foundations & the "Outing" concept

## Goal

Right now, splitting a ₹600 dinner bill requires creating a full "Trip" with a name, destination, start date, end date, and budget. That friction is likely a big reason the app "isn't useful right now." This phase introduces a **lightweight Outing** — same underlying data model as a Trip, but with almost everything optional — so quick one-off gatherings take seconds to create.

Everything here is additive. No existing trip, expense, or task breaks.

## 1. Data model changes

### `Trip` (`app/src/main/java/com/manikandan/tripoo/data/model/Trip.kt`)

Add one field:

```kotlin
data class Trip(
    val id: String = "",
    val name: String = "",
    val destination: String = "",
    val description: String = "",
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val budget: Double = 0.0,
    val adminId: String = "",
    val joinCode: String = "",
    val memberIds: List<String> = emptyList(),
    val status: String = "upcoming",
    val type: String = "trip"        // NEW: "trip" | "outing"
)
```

- Existing documents read `type` as `"trip"` automatically (Firestore fills missing fields with the Kotlin default), so **no backfill migration is required**.
- `status` (`upcoming`/`active`/`past`) still applies to Trips. For Outings, treat status as always `"active"` on the day of creation and `"past"` after — outings don't need an "upcoming" countdown since they're same-day by default (see below).

### Outing-specific field defaults

When creating an Outing:
- `startDate` = `endDate` = creation timestamp (so `deriveStatus()` in `TripRepository`/`tripUtils.ts` keeps working unmodified).
- `budget` = `0.0` unless the user explicitly sets one (budget card on Home is hidden when `budget <= 0`, which already happens today).
- `destination` = optional free text (e.g., "Marina Diner") — used only for display, not maps-required.

No changes needed to `ExpenseRepository`, `TaskRepository`, or their Firestore paths — outings use the exact same `trips/{id}/expenses`, `trips/{id}/tasks`, `trips/{id}/members` subcollections as trips.

## 2. Firestore rules changes (`firestore.rules`)

`isLeaderTripDetailsUpdate()` currently whitelists these fields for edit:
```
['name','destination','description','startDate','endDate','budget','status']
```
Add `'type'` to that list so it can be set on create and never needs to change afterward — `type` should actually be **immutable after creation** in practice, so instead of adding it to the *update* whitelist, just make sure `create` allows it (creation already allows any fields since the rule only checks `adminId`). No rule change is strictly required for `create`; only note that `type` must **not** appear in `isLeaderTripDetailsUpdate()`'s allowed keys, so it can't be changed post-creation (an outing can't silently become a trip or vice versa — if a user needs that, they create a new one).

## 3. Creation flow changes

### New entry point

Replace the single "New Trip" button (Dashboard `btnNewTrip`, Home empty-state `btnCreateTrip`) with a **choice bottom sheet**:

```
┌─────────────────────────────┐
│   What are you planning?    │
│                              │
│  🧳  Full Trip                │
│     Multi-day, with dates    │
│     and a budget             │
│                              │
│  🍔  Quick Outing              │
│     Dinner, movie, a day out │
│     — no dates needed        │
└─────────────────────────────┘
```

- Tapping **Full Trip** → existing `CreateTripFragment` flow, unchanged.
- Tapping **Quick Outing** → same fragment, but with `type=outing` passed as an argument, which:
  - Hides the Start Date / End Date / Budget fields entirely.
  - Adds an optional single-row "category" chip selector: 🍽 Food · 🎬 Movie · 🎉 Party · 🛍 Shopping · 📦 Other (stored in `description` as a short tag, or as a new lightweight `category` field if you want it queryable later — for Phase 1, storing it as plain text in `description` is enough and avoids another schema change).
  - Submit button reads "Create Outing" instead of "Create Trip".

### Files to touch (Android)

| File | Change |
|---|---|
| `CreateTripFragment.java` | Read a new `type` nav argument; conditionally hide date/budget views; change title/button text; pass `type` into the `Trip` object on submit |
| `fragment_create_trip.xml` | Wrap date row + budget row in a container with an id so it can be `View.GONE` for outings |
| `nav_graph.xml` | Add `type` argument (default `"trip"`) to `createTripFragment` destination |
| `TripDashboardFragment.java` | Replace direct navigation from `btnNewTrip` with the new choice bottom sheet |
| `HomeFragment.java` | Same replacement for `btnCreateTrip` in the no-trip empty state (see Phase 5 for why this empty state should ideally not be reachable at all) |
| New: `CreateChoiceBottomSheet.kt` | Small `BottomSheetDialogFragment` with the two options above, navigates onward with the chosen `type` |

## 4. Dashboard changes

`TripDashboardFragment` / `TripCardAdapter`:

- Add a small type badge on each `item_trip_card.xml` (e.g., a tiny "OUTING" pill next to the status badge) so outings and trips are visually distinguishable in the same list — no separate screen needed.
- Add an "Outings" filter chip alongside the existing All/Active/Upcoming/Past chips (`chipAll`/`chipActive`/`chipUpcoming`/`chipPast` in `fragment_trip_dashboard.xml`), filtering `trip.type == "outing"`.
- `TripDashboardViewModel.applyFilter()` gets one more branch for this chip.

Outings and Trips otherwise **share the same dashboard list, same card component, same Home/Expenses/Tasks/Groups tabs**. This keeps the mental model simple: "everything you're part of is a gathering; some have dates, some don't."

## 5. Home screen changes for Outings

`HomeFragment` already brands its hero section around a countdown. For `trip.type == "outing"`:

- Hide the countdown card (`llCountdownTiles` / `tripCountdownView`) entirely — outings don't count down.
- Replace it with a simple header: outing name, category emoji, member avatar row, and a **prominent "Add Expense" button** front and center (since for outings, expense splitting is the main reason someone opened the screen).
- Budget card (`llBudgetProgress` etc.) only shows if `budget > 0` — already true today, no change needed.
- Quick Access grid keeps Expenses/Tasks/Groups as-is (Phase 3 adds more tiles for trip-planning features, which can be conditionally hidden for outings since they're less relevant there — see Phase 3).

## 6. Testing checklist for this phase

- [ ] Existing trips (created before this change) still load, display "Full Trip" implicitly, and behave exactly as before.
- [ ] Creating an Outing produces a trip document with `type="outing"`, `startDate == endDate == now`, `budget == 0`.
- [ ] Outing appears on Dashboard with the "OUTING" badge and under the new "Outings" filter chip.
- [ ] Opening an Outing's Home screen shows no countdown, shows the Add Expense CTA.
- [ ] Expenses/Tasks/Groups tabs work identically for an Outing as for a Trip (same subcollections, same permissions).
- [ ] Deleting an Outing behaves exactly like deleting a Trip (same cleanup code path in `TripRepository.deleteTripAsAdmin`).
