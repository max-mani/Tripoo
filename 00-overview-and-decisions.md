# Ulla Redesign — Overview & Roadmap

## Why this redesign

The app currently forces every gathering — a two-day trip or a Tuesday dinner — through the same heavyweight "Trip" model (dates, budget, destination, join code). Expense splitting only shows *your* aggregate owe/owed, not who actually needs to pay whom. Trip "planning" is just a countdown timer. This roadmap fixes those three things without touching the visual branding.

## Decisions locked in (from our Q&A)

| Question | Decision |
|---|---|
| Primary identity | **Trip planner first**, expense splitting is a feature inside it |
| Outings vs Trips | Both — add a lightweight, date-optional **Outing** type alongside full **Trips** |
| Settlement | Need (a) simplified "who pays whom" debt minimization, (b) one-tap mark-settled, (c) clear per-person balance visibility |
| Friends model | Keep per-trip membership (join code), but **auto-suggest people you've traveled with before** when creating a new trip/outing |
| Platform order | **Android first**, web parity later |
| Visual redesign | **Keep current orange branding/layout** — fix confusing flows and screens, don't restyle |

## What "done" looks like

- Creating a quick outing takes under 15 seconds (name + a few people, no forced dates/budget).
- Opening "Settle Up" tells you exactly who to pay or collect from, in the fewest possible transactions — not just your own totals.
- Home screen for a trip actually helps you plan it (itinerary, decisions/polls, shared notes), not just count down to it.
- Adding people to a new trip surfaces the friends you traveled with last time, instead of forcing you to remember a join code.
- No screen change requires you to relearn the app — same colors, same bottom nav, same visual language.

## Phase index

| Phase | File | What it ships | Depends on |
|---|---|---|---|
| 1 | `01-phase1-foundations-and-outings.md` | Outing type, data model groundwork, dashboard filter | — |
| 2 | `02-phase2-settlement-and-balances.md` | Group debt ledger, debt simplification, Settle Up screen | Phase 1 (trip `type` field is unrelated but ships first for sequencing) |
| 3 | `03-phase3-trip-planning-features.md` | Itinerary, polls, shared notes | Phase 1 |
| 4 | `04-phase4-people-and-recent-collaborators.md` | "Recently traveled with" smart invite picker | Phase 1 |
| 5 | `05-phase5-flow-and-ux-fixes.md` | Fixes confusing existing flows (Dashboard/Home overlap, task/expense permissions, labeling) | Can be done anytime; recommended last so it also cleans up new screens |

Do them in order 1 → 2 → 3 → 4 → 5. Phase 1 is small and mostly additive (safe to ship first). Phases 2–4 are independent of each other once Phase 1 lands, so you could reorder 2/3/4 if one matters more to you right now — just do Phase 5 last since it references things introduced earlier.

## Explicitly out of scope (for now)

Flagged so you don't wonder if they were forgotten:

- **Web parity** — everything below is written Android-first. Once you're happy with an Android phase, we mirror it to `frontend/`.
- **Multi-currency** — everything stays INR (₹) as today.
- **Real in-app invite system** (organiser adds a uid directly, invitee gets a pending-invite notification) — the current Firestore rules only allow a user to add *themselves* to `memberIds`. Phase 4 works around this with a "smart share" shortcut instead of true silent invites. A real invite system is called out as a future stretch inside Phase 4 if you want it later.
- **Payment gateway / UPI deep links** — settlement is tracked in-app only; "mark as settled" does not move real money.
- **Weather, photo galleries** — mentioned in Phase 3 as nice-to-haves but not built now.

## Global technical notes that apply across all phases

- **No Cloud Functions in this project.** Everything is client-writes + Firestore security rules. Every new subcollection below needs a corresponding rule block, mirroring the existing pattern in `firestore.rules` for `expenses`/`tasks`.
- **Backward compatibility.** Every new field on `Trip`/`User` gets a sensible default so existing documents keep working without a migration script (Kotlin data class defaults handle this automatically on read).
- **Fan-out notifications.** New actions (settlements, polls, itinerary changes) should publish through the existing `FanoutNotificationPublisher` so they show up as local notifications for other members, consistent with how expense/task events work today.
