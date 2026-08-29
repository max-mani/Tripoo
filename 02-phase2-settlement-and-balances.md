# Phase 2 — Settlement & Balances ("who pays whom")

## Goal

Today, `ExpensesViewModel`/`ExpenseRepository` only compute **your own** aggregate you-owe / you're-owed across all unsettled expenses. There is no group-wide "who pays whom" view, and "settled" is tracked per-expense, which gets messy once a group has 15+ shared expenses. This phase adds:

1. A proper **group debt ledger** (who owes whom, not just what *you* owe).
2. **Debt simplification** — the minimum set of payments that clears all balances.
3. A **Settle Up** screen with one-tap "mark as settled".
4. A **Settlements** history, separate from individual expenses, so one payment can clear multiple expenses at once.

## 1. The core problem with today's model

`ExpensesViewModel.processExpenses()` computes:
```
youOwe = sum of your share on expenses paid by others
youAreOwed = sum of others' share on expenses you paid
```
This tells *you* two numbers, but doesn't tell the group who should actually pay whom, and marking an expense "settled" is all-or-nothing per expense (only a leader can do it), which doesn't match how people actually settle — usually with one lump payment covering several expenses.

## 2. New data: Settlements subcollection

`trips/{tripId}/settlements/{settlementId}`:

```kotlin
data class Settlement(
    val id: String = "",
    val fromUserId: String = "",   // who paid
    val toUserId: String = "",     // who received
    val amount: Double = 0.0,
    val note: String? = null,
    val createdBy: String = "",    // for permission checks, mirrors Expense/Task pattern
    val timestamp: Long = System.currentTimeMillis()
)
```

A settlement is an **offsetting entry** in the ledger — it doesn't touch any individual `Expense` document. This is simpler and matches how Splitwise-style apps work: expenses build up debt, settlements pay it down, and the two are reconciled at read time.

### Firestore rules addition

Mirror the `expenses` rule block:

```
match /settlements/{settlementId} {
  function settlementTripMember() {
    return request.auth.uid in tripSnap().memberIds;
  }
  allow read: if isAuthenticated() && settlementTripMember();
  allow create: if isAuthenticated()
    && settlementTripMember()
    && request.resource.data.createdBy == request.auth.uid
    && (request.resource.data.fromUserId == request.auth.uid
        || request.resource.data.toUserId == request.auth.uid);
  allow delete: if isAuthenticated()
    && settlementTripMember()
    && (isTripLeaderUid(request.auth.uid) || resource.data.createdBy == request.auth.uid);
  allow update: if false; // settlements are immutable — delete + recreate if wrong
}
```

The `create` rule requires the creator to be one of the two parties, so a random third member can't fabricate a settlement between two other people.

## 3. The ledger algorithm

New pure functions (Android: add to `ExpensesViewModel.kt` or a new `SettlementCalculator.kt` util; Web equivalent: `frontend/src/lib/expenseBalances.ts`).

### Step 1 — Pairwise debt from expenses

For every unsettled-relevant expense (all expenses now count toward the ledger — see note below on retiring per-expense "settled"), for every member in `splitWith` who isn't the payer:
```
debt[debtor][payer] += expense.amount / expense.splitWith.size
```

### Step 2 — Apply settlements as offsets

For every `Settlement`:
```
debt[fromUserId][toUserId] -= settlement.amount
```

### Step 3 — Collapse to net balance per person

```
net[uid] = sum(debt[uid][*]) - sum(debt[*][uid])
```
Positive `net[uid]` = that person is owed money overall; negative = they owe money overall.

### Step 4 — Simplify into minimum transactions

Classic greedy debt-simplification:
1. Split members into creditors (`net > 0`) and debtors (`net < 0`).
2. Repeatedly take the largest creditor and largest debtor, settle `min(|creditor|, |debtor|)` between them, push a `Transaction(from=debtor, to=creditor, amount)` into the result list, reduce both by that amount, drop whichever hit zero.
3. Repeat until all balances are zero (or within a rounding epsilon, e.g. ₹0.01).

This is O(n log n) and gives the minimum number of payments needed to settle the whole group — much clearer than showing every pairwise debt.

> **Note on retiring per-expense `settled`:** Once Settlements exist, the per-expense `settled` boolean becomes redundant and confusing (an expense being "settled" doesn't map to a real-world action anymore — payments settle *balances*, not individual expenses). Recommendation: **stop writing to `expense.settled` going forward**, keep the field for backward compatibility with old data, but drive all new UI off the ledger + settlements instead. The existing "Settled" tab on Expenses becomes "Settlement History" (see below) rather than a per-expense filter.

## 4. New screen: Settle Up

Replace the current Expenses tab bar (`All Expenses | My Spending | Settled | Stats`) with:

```
All Expenses | Settle Up | Stats
```

("My Spending" folds into a filter chip inside "All Expenses" rather than a full tab, since it's a subset view, not a distinct concept — optional, low priority, keep if you'd rather not touch it.)

### Settle Up screen layout

1. **Your balance, large and clear** at the top:
   - Green "You are owed ₹X overall" or red "You owe ₹X overall" (from `net[currentUserId]`).
2. **Suggested payments** — the simplified transaction list, filtered to only rows touching the current user:
   - `You → Alex · ₹450` with a **"Mark as paid"** button.
   - `Sam → You · ₹120` with a **"Mark as received"** button.
   - Tapping either opens a confirm dialog, then writes a `Settlement` document (`fromUserId`/`toUserId` taken from the row, `amount` prefilled but editable in case a partial payment was made).
3. **Everyone's balances** — a simple list of every member with their net figure, for group transparency (helps when someone asks "wait, does X still owe money?").
4. **Settlement history** — reverse-chronological list of past `Settlement` documents ("Alex paid you ₹200 on Jun 12"), each deletable by its creator or a leader (undo mistakes).

### Files to touch (Android)

| File | Change |
|---|---|
| New: `data/model/Settlement.kt` | Model above |
| `ExpenseRepository.kt` | Add `listenToSettlements(tripId)`, `addSettlement(tripId, settlement)`, `deleteSettlement(tripId, id)` |
| `ExpensesViewModel.kt` | Add ledger computation (`computeNetBalances`, `simplifyDebts`), expose `LiveData<List<Transaction>>` and `LiveData<List<Settlement>>` |
| New: `ui/expenses/SettleUpFragment.kt` + `fragment_settle_up.xml` | The screen above, hosted inside the existing Expenses tab container (swap in/out like the current Stats view does via `statsContainer`) |
| `ExpensesFragment.kt` | Replace `tabSettled` with `tabSettleUp`, wire to show `SettleUpFragment`'s view instead of the old filtered list |
| `item_expense.xml` / `ExpenseAdapter.kt` | Per-expense "Mark as settled" menu item can be removed once Settle Up ships (or kept as a deprecated no-op-with-tooltip pointing to Settle Up — recommend removing to avoid two competing settle affordances) |
| `firestore.rules` | Add the `settlements` block above |

### Web (deferred, noted for later parity)

Same logic lives in `frontend/src/lib/expenseBalances.ts` (extend it with `computeNetBalances`/`simplifyDebts`) and a new `frontend/src/services/settlementService.ts` mirroring `expenseService.ts`. Not built in this Android-first pass — flagged here so the Android data model doesn't accidentally diverge from what web will need later.

## 5. Notifications

Extend `FanoutNotificationPublisher` usage: when a settlement is created, publish `"${fromName} paid ${toName} ₹${amount}"` with type `"settlement_added"`, same pattern as `"expense_added"`.

## 6. Testing checklist

- [ ] A group of 3 people with several criss-crossing expenses reduces to the mathematically minimal number of suggested payments (verify by hand on a small example: A pays 300 split 3 ways, B pays 150 split 3 ways → should net to a single suggested payment, not two).
- [ ] Marking a suggested payment as settled removes it (or reduces its amount) from the Suggested Payments list on next load.
- [ ] Settlement history shows all past settlements, newest first, and deleting one restores the corresponding balance.
- [ ] A non-member cannot create a settlement for two other users (rules test).
- [ ] Old trips with `expense.settled = true` data don't break the new ledger (settled flag is simply ignored by the new calculation).
