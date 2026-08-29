package com.manikandan.tripoo.ui.expenses

import com.manikandan.tripoo.data.model.Expense
import com.manikandan.tripoo.data.model.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementCalculatorTest {

    @Test
    fun threePersonCrissCrossReducesToOnePayment() {
        val a = "A"
        val b = "B"
        val c = "C"
        val expenses = listOf(
            Expense(id = "1", amount = 300.0, paidBy = a, splitWith = listOf(a, b, c)),
            Expense(id = "2", amount = 150.0, paidBy = b, splitWith = listOf(a, b, c), settled = true)
        )
        val net = SettlementCalculator.computeNetBalances(expenses, emptyList(), listOf(a, b, c))
        val payments = SettlementCalculator.simplifyDebts(net)
        assertEquals(1, payments.size)
        assertEquals(c, payments[0].fromUserId)
        assertEquals(a, payments[0].toUserId)
        assertEquals(150.0, payments[0].amount, SettlementCalculator.EPS)
        assertTrue(SettlementCalculator.isZero(net[b] ?: 0.0))
    }

    @Test
    fun settlementOffsetsSuggestedPayment() {
        val a = "A"
        val b = "B"
        val expenses = listOf(
            Expense(id = "1", amount = 100.0, paidBy = a, splitWith = listOf(a, b))
        )
        val settlements = listOf(
            Settlement(id = "s1", fromUserId = b, toUserId = a, amount = 50.0)
        )
        val net = SettlementCalculator.computeNetBalances(expenses, settlements, listOf(a, b))
        val payments = SettlementCalculator.simplifyDebts(net)
        assertTrue(payments.isEmpty())
        assertTrue(SettlementCalculator.isZero(net[a] ?: 0.0))
        assertTrue(SettlementCalculator.isZero(net[b] ?: 0.0))
    }
}
