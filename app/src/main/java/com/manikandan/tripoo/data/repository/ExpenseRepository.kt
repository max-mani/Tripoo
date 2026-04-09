package com.manikandan.tripoo.data.repository

import com.manikandan.tripoo.data.model.Expense
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ExpenseRepository {
    private val db = FirebaseFirestore.getInstance()

    data class ExpenseSummary(
        val topSpenderId: String = "",
        val topSpenderName: String = "",
        val topSpenderAmount: Double = 0.0,
        val totalSpent: Double = 0.0,
        val averagePerPerson: Double = 0.0
    )

    suspend fun addExpense(tripId: String, expense: Expense) {
        try {
            val ref = db.collection("trips").document(tripId).collection("expenses").document()
            db.collection("trips").document(tripId).collection("expenses")
                .document(ref.id).set(expense.copy(id = ref.id)).await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun listenToExpenses(tripId: String): Flow<List<Expense>> = callbackFlow {
        val listener = db.collection("trips").document(tripId).collection("expenses")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull {
                    it.toObject(Expense::class.java)?.copy(id = it.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun calculateBalances(tripId: String, currentUserId: String): Pair<Double, Double> {
        return try {
            val expenses = db.collection("trips").document(tripId).collection("expenses")
                .get().await().documents.mapNotNull { it.toObject(Expense::class.java) }
            var youOwe = 0.0
            var youAreOwed = 0.0
            expenses.forEach { expense ->
                val share = expense.amount / expense.splitWith.size.coerceAtLeast(1)
                if (expense.paidBy != currentUserId && expense.splitWith.contains(currentUserId)) {
                    youOwe += share
                } else if (expense.paidBy == currentUserId) {
                    val othersShare = expense.splitWith.filter { it != currentUserId }.size * share
                    youAreOwed += othersShare
                }
            }
            Pair(youOwe, youAreOwed)
        } catch (e: Exception) {
            Pair(0.0, 0.0)
        }
    }

    suspend fun getTotalExpenses(tripId: String): Double {
        return try {
            db.collection("trips").document(tripId).collection("expenses")
                .get().await().documents
                .mapNotNull { it.toObject(Expense::class.java) }
                .sumOf { it.amount }
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun saveExpenseSummary(
        tripId: String,
        topSpenderId: String,
        topSpenderName: String,
        topSpenderAmount: Double,
        totalSpent: Double,
        averagePerPerson: Double
    ) {
        val payload = mapOf(
            "topSpenderId" to topSpenderId,
            "topSpenderName" to topSpenderName,
            "topSpenderAmount" to topSpenderAmount,
            "totalSpent" to totalSpent,
            "averagePerPerson" to averagePerPerson,
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("trips").document(tripId)
            .collection("meta").document("expense_summary")
            .set(payload).await()
    }

    suspend fun getExpenseSummary(tripId: String): ExpenseSummary? {
        return try {
            val doc = db.collection("trips").document(tripId)
                .collection("meta").document("expense_summary")
                .get().await()
            if (!doc.exists()) return null
            ExpenseSummary(
                topSpenderId = doc.getString("topSpenderId").orEmpty(),
                topSpenderName = doc.getString("topSpenderName").orEmpty(),
                topSpenderAmount = doc.getDouble("topSpenderAmount") ?: 0.0,
                totalSpent = doc.getDouble("totalSpent") ?: 0.0,
                averagePerPerson = doc.getDouble("averagePerPerson") ?: 0.0
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun markExpenseSettled(tripId: String, expenseId: String, isSettled: Boolean = true) {
        db.collection("trips").document(tripId).collection("expenses")
            .document(expenseId)
            .update("settled", isSettled)
            .await()
    }

    fun listenToExpenses(tripId: String, callback: (List<Expense>, Exception?) -> Unit): ListenerRegistration {
        return db.collection("trips").document(tripId).collection("expenses")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    callback(emptyList(), e)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Expense::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                callback(list, null)
            }
    }

    fun addExpense(tripId: String, expense: Expense, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { addExpense(tripId, expense) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    suspend fun updateExpense(tripId: String, expenseId: String, expense: Expense) {
        try {
            val docRef = db.collection("trips").document(tripId).collection("expenses").document(expenseId)
            val existing = docRef.get().await()
            // Read existing settled state; once settled it can never be unset
            val existingSettled = existing.getBoolean("settled") == true
            val finalSettled = expense.settled || existingSettled
            val updates = mapOf(
                "title" to expense.title,
                "amount" to expense.amount,
                "category" to expense.category,
                "paidBy" to expense.paidBy,
                "splitWith" to expense.splitWith,
                "timestamp" to expense.timestamp,
                "settled" to finalSettled
            )
            docRef.update(updates).await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun updateExpense(tripId: String, expenseId: String, expense: Expense, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { updateExpense(tripId, expenseId, expense) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    suspend fun deleteExpense(tripId: String, expenseId: String) {
        try {
            db.collection("trips").document(tripId).collection("expenses").document(expenseId).delete().await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun deleteExpense(tripId: String, expenseId: String, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { deleteExpense(tripId, expenseId) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }
}
