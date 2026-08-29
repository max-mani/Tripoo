# Phase 5 — Flow & UX Fixes

## Goal

You asked to **keep the current branding and layout** but fix confusing flows. This phase is a punch list of concrete problems found in the existing code, each with a specific fix. Do this phase last so it also cleans up anything Phases 1–4 introduced.

None of these require new screens or visual redesign — they're navigation logic, permission logic, and labeling fixes.

---

## 1. Dashboard vs. Home overlap is confusing

**Problem:** `TripDashboardFragment` (list of all your trips) and `HomeFragment` (a single trip) both have their own "no trip yet" empty state with Create/Join buttons (`llNoTrip` in `fragment_home.xml`). This means there are two different places in the app that both look like "the start screen," and it's unclear which one you're supposed to land on.

**Fix:**
- `HomeFragment` should only ever be navigated to **with a valid `tripId`**. If somehow reached without one (e.g., stale `lastActiveTripId`), redirect immediately to `TripDashboardFragment` instead of showing its own empty state.
- Remove `llNoTrip` and its Create/Join buttons from `fragment_home.xml` entirely; Dashboard becomes the single, unambiguous "no trips yet" screen (see item 6 below for improving *that* empty state).
- Files: `HomeFragment.java` (guard clause at top of `onViewCreated`/`observeUser` branch), `fragment_home.xml` (remove `llNoTrip` block), keep `llInTrip` as the only branch.

## 2. Quick Access grid duplicates the bottom nav

**Problem:** Home's Quick Access grid shows Expenses / Tasks / Participants — but the bottom nav directly below already has Expenses / Tasks / Groups. Same three destinations, shown twice on the same screen.

**Fix:**
- Once Phase 3 ships (Itinerary / Polls / Notes), Quick Access shows **only the new planning tools**, not the ones duplicated in bottom nav.
- If you do Phase 5 before Phase 3, temporarily just remove the redundant tiles and leave Quick Access empty/hidden until Phase 3 fills it back in.
- Files: `fragment_home.xml` (Quick Access `LinearLayout` contents), `HomeFragment.java` (remove `qaExpenses`/`qaTasks`/`qaGroups` click listeners).

## 3. "Groups" is an unclear label for "the people in this trip"

**Problem:** The bottom nav tab and fragment are called "Groups" (`GroupsFragment`, nav id `participantsFragment`), but it's actually just the member list for *this one trip* — "Groups" implies something bigger (like multiple groups/circles), which doesn't exist in this app.

**Fix:**
- Rename the **user-facing label only** to "Members" or "People" — change the string in `bottom_nav_menu.xml` / the hardcoded `tvNavGroups.text` calls, and the screen title in `fragment_groups.xml`.
- Keep internal identifiers (`GroupsFragment`, `participantsFragment`, `navGroups` view IDs) unchanged to avoid a large rename diff — this is a display-string-only change.
- Files: every place that sets `"Groups"` as visible text: `fragment_groups.xml` (header), all `tvNavGroups`/`tvNavExpenses`-sibling text in `fragment_home.xml`, `fragment_expenses.xml`, `fragment_tasks.xml`, `fragment_groups.xml` (each screen duplicates this bottom nav layout, so the string needs updating in all four), `strings.xml` if you centralize it (recommended: extract `"Home"`, `"Expenses"`, `"Tasks"`, `"Members"` into `strings.xml` now so future copy changes are one-line edits instead of four).

## 4. Task completion permission is backwards from what people expect

**Problem:** Per `firestore.rules`, only a trip leader can flip `completed` on a task — even the person who *created* the task, or the person it's *assigned to*, cannot check it off themselves unless they're also a leader. `TaskAdapter`'s comment even flags this: "Toggle on checkbox tap (leaders only)."

**Fix — pick the rule that matches how your group actually uses tasks:**
- **Recommended:** allow completion toggle by **the leader, the task's creator, OR the person it's assigned to** (not just leader). This matches intuition: "if it's my task, I can check it off."
- Firestore rule change (`firestore.rules`, `tasks/{taskId}` → `allow update`):
  ```
  allow update: if isAuthenticated()
    && taskTripMember()
    && (
      (isTripLeaderUid(request.auth.uid)
        && request.resource.data.createdBy == resource.data.get('createdBy', ''))
      || (resource.data.get('createdBy', '') == request.auth.uid
          && request.resource.data.createdBy == resource.data.get('createdBy', '')
          && request.resource.data.completed == resource.data.get('completed', false))  // creator: everything except completed
      || (resource.data.get('assignedTo', '') == request.auth.uid
          && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['completed']))  // assignee: completed only
      || isDeadlineNotifiedBumpOnly()
    );
  ```
- Android: `TaskAdapter.kt` checkbox enablement — change from `canManageTripAsLeader` only to `canManageTripAsLeader || task.assignedTo == currentUserId`.
- `TasksViewModel.toggleTask()` — remove the `canUserManageTripAsLeader` gate, replace with "is leader or is assignee."

## 5. Expense settle permission should account for Phase 2

**Problem:** Currently only a leader can mark an expense settled. Once Phase 2's Settlements ship, per-expense settling is being retired anyway (see Phase 2 notes) — so this item is really "make sure the old per-expense settle button is fully removed," not a new permission rule.

**Fix:**
- Confirm `ExpenseAdapter`'s "Mark as settled" popup menu item and `ExpensesFragment.markAsSettled()` are deleted once Phase 2's Settle Up screen ships.
- Confirm `firestore.rules`'s `expenses/{expenseId}` block no longer needs the `settled` special-casing in `allow update` (it can stay harmlessly since nothing writes to it anymore, but clean it up if you want a smaller ruleset).

## 6. Weak Dashboard empty state

**Problem:** A brand-new user with zero trips sees a generic message and two small buttons — not very inviting for what's supposed to be the app's home base now that Dashboard is the single unambiguous start screen (per item 1).

**Fix:**
- Add a friendly onboarding empty state: short illustration/icon, one line explaining Trips vs. Outings ("Plan a multi-day trip, or start a quick outing — split the bill either way"), and two clearly primary buttons: **New Trip** / **New Outing** (using Phase 1's choice bottom sheet), plus a smaller **Join with a code** link below.
- Files: `fragment_trip_dashboard.xml` (empty-state container, currently minimal/absent — add one), `TripDashboardFragment.java` (show/hide based on `filteredTrips.isEmpty()`).

## 7. Notification types need extending

**Problem:** `FanoutNotificationPublisher` has a fixed set of `type` strings (`member_joined`, `expense_added`, etc.) that doesn't yet cover Phase 2/3 features.

**Fix:** Add `settlement_added`, `poll_created`, `poll_closed`, `itinerary_updated` as they're built, following the exact existing call pattern — no structural change needed, just more call sites.

## 8. Bottom-sheet visual consistency (light touch)

**Problem:** Not really a redesign issue, but worth a pass: `AddExpenseBottomSheet`, `AddTaskBottomSheet`, and the new sheets from Phases 1/3/4 should all share the same header style (drag handle, title, close button) — they already mostly do (`bg_bottom_sheet_rounded`, `bg_sheet_handle`, `btnClose` pattern). Just make sure every **new** sheet copies this exact structure rather than inventing a new header pattern, so it stays invisible/consistent to the user.

---

## Suggested order within this phase

1. Item 1 (Dashboard/Home guard) — highest impact, low risk.
2. Item 3 (Groups → Members label) — trivial, do it early since it's copy-only.
3. Item 4 (task permissions) — needs a rules deploy + Firestore rules test, do it deliberately.
4. Item 2 (Quick Access dedupe) — do alongside/after Phase 3 lands.
5. Item 5 (retire per-expense settle) — do alongside/after Phase 2 lands.
6. Items 6, 7, 8 — polish, any time, low risk.

## Testing checklist

- [ ] Cold-starting the app with `lastActiveTripId` pointing at a deleted trip lands cleanly on Dashboard, not a broken Home screen.
- [ ] Every screen's bottom nav shows "Members" (or your chosen label) consistently across Home/Expenses/Tasks/Groups.
- [ ] A task's assignee (not creator, not leader) can check it off; a random other member cannot.
- [ ] New Dashboard empty state renders correctly for a genuinely new account (0 trips, 0 outings).
