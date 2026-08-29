package com.manikandan.tripoo.ui.expenses

import com.manikandan.tripoo.data.model.Expense
import com.manikandan.tripoo.data.model.Settlement
import kotlin.math.abs
import kotlin.math.min

/** Group ledger: all expenses count; [Expense.settled] is ignored. */
object SettlementCalculator {
    const val EPS = 0.01

    data class SuggestedPayment(
        val fromUserId: String,
        val toUserId: String,
        val amount: Double
    )

    fun computeNetBalances(
        expenses: List<Expense>,
        settlements: List<Settlement>,
        memberIds: Collection<String> = emptyList()
    ): Map<String, Double> {
        val debt = mutableMapOf<String, MutableMap<String, Double>>()
        for (expense in expenses) {
            val share = expense.amount / expense.splitWith.size.coerceAtLeast(1)
            for (uid in expense.splitWith) {
                if (uid.isBlank() || uid == expense.paidBy) continue
                val row = debt.getOrPut(uid) { mutableMapOf() }
                row[expense.paidBy] = (row[expense.paidBy] ?: 0.0) + share
            }
        }
        for (s in settlements) {
            if (s.fromUserId.isBlank() || s.toUserId.isBlank()) continue
            val row = debt.getOrPut(s.fromUserId) { mutableMapOf() }
            row[s.toUserId] = (row[s.toUserId] ?: 0.0) - s.amount
        }

        val ids = LinkedHashSet<String>()
        ids.addAll(memberIds.filter { it.isNotBlank() })
        ids.addAll(debt.keys)
        debt.values.forEach { ids.addAll(it.keys) }
        expenses.forEach { e ->
            if (e.paidBy.isNotBlank()) ids.add(e.paidBy)
            ids.addAll(e.splitWith.filter { it.isNotBlank() })
        }
        settlements.forEach {
            if (it.fromUserId.isNotBlank()) ids.add(it.fromUserId)
            if (it.toUserId.isNotBlank()) ids.add(it.toUserId)
        }

        val net = mutableMapOf<String, Double>()
        for (uid in ids) {
            val shouldPay = debt[uid]?.values?.sum() ?: 0.0
            val shouldReceive = debt.values.sumOf { it[uid] ?: 0.0 }
            net[uid] = shouldReceive - shouldPay
        }
        return net
    }

    fun simplifyDebts(net: Map<String, Double>): List<SuggestedPayment> {
        val debtors = net.filter { it.value < -EPS }
            .map { it.key to -it.value }
            .toMutableList()
        val creditors = net.filter { it.value > EPS }
            .map { it.key to it.value }
            .toMutableList()
        val result = mutableListOf<SuggestedPayment>()
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            debtors.sortByDescending { it.second }
            creditors.sortByDescending { it.second }
            val (debtorId, debtorAmt) = debtors.removeAt(0)
            val (creditorId, creditorAmt) = creditors.removeAt(0)
            val pay = min(debtorAmt, creditorAmt)
            if (pay >= EPS) {
                result.add(SuggestedPayment(debtorId, creditorId, roundMoney(pay)))
            }
            val dLeft = debtorAmt - pay
            val cLeft = creditorAmt - pay
            if (dLeft > EPS) debtors.add(debtorId to dLeft)
            if (cLeft > EPS) creditors.add(creditorId to cLeft)
        }
        return result
    }

    private fun roundMoney(value: Double): Double =
        kotlin.math.round(value * 100.0) / 100.0

    fun isZero(value: Double): Boolean = abs(value) < EPS
}
