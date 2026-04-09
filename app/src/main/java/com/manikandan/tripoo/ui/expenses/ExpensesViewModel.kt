package com.manikandan.tripoo.ui.expenses

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manikandan.tripoo.data.model.Expense
import com.manikandan.tripoo.data.model.Trip
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.data.repository.AuthRepository
import com.manikandan.tripoo.data.repository.ExpenseRepository
import com.manikandan.tripoo.data.repository.TripRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ExpenseFilter { ALL, MY_SPENDING, SETTLED, STATS }

class ExpensesViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: String = savedStateHandle.get<String>("tripId") ?: ""
    private val authRepo = AuthRepository()
    private val expenseRepo = ExpenseRepository()
    private val tripRepo = TripRepository()

    val trip = MutableLiveData<Trip?>()
    val members = MutableLiveData<List<TripMember>>()
    val youOwe = MutableLiveData(0.0)
    val youAreOwed = MutableLiveData(0.0)
    val oweTrendPct = MutableLiveData(0)
    val owedTrendPct = MutableLiveData(0)
    val tabAllItems = MutableLiveData<List<ExpenseAdapter.ExpenseListItem>>(emptyList())
    val tabMyItems = MutableLiveData<List<ExpenseAdapter.ExpenseListItem>>(emptyList())
    val tabSettledItems = MutableLiveData<List<ExpenseAdapter.ExpenseListItem>>(emptyList())
    val statsData = MutableLiveData(ExpenseStats())
    val isLoading = MutableLiveData(true)
    val errorMessage = MutableLiveData<String?>()

    val currentUserId: String
        get() = authRepo.getCurrentUser()?.uid ?: ""

    private var allExpenses: List<Expense> = emptyList()

    data class ExpenseStats(
        val total: Double = 0.0,
        val byCategory: Map<String, Double> = emptyMap(),
        val topSpender: Pair<String, Double>? = null,
        val avgPerPerson: Double = 0.0
    )

    init {
        loadTripAndMembers()
        loadPersistedSummary()
        collectExpenses()
    }

    private fun loadTripAndMembers() {
        viewModelScope.launch {
            try {
                val t = tripRepo.getTrip(tripId)
                trip.postValue(t)
                val list = tripRepo.getTripMembers(tripId)
                members.postValue(list)
                // Pass the freshly loaded list directly to avoid LiveData postValue race condition
                // (postValue is async so members.value would still be null at this point)
                processExpenses(allExpenses, memberList = list)
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    private fun collectExpenses() {
        viewModelScope.launch {
            try {
                expenseRepo.listenToExpenses(tripId).collect { expenses ->
                    isLoading.postValue(false)
                    allExpenses = expenses
                    // Use the currently known members (may be empty on first emission —
                    // loadTripAndMembers will call processExpenses again once members arrive)
                    processExpenses(expenses, memberList = members.value)
                }
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    private fun processExpenses(expenses: List<Expense>, memberList: List<TripMember>? = null) {
        val currentMembers = memberList ?: members.value.orEmpty()

        tabAllItems.postValue(buildItems(expenses))
        tabMyItems.postValue(
            buildItems(expenses.filter { it.paidBy == currentUserId || it.splitWith.contains(currentUserId) })
        )
        tabSettledItems.postValue(
            buildItems(expenses.filter { it.settled })
        )

        var owe = 0.0
        var owed = 0.0
        expenses.forEach { expense ->
            if (expense.settled) return@forEach
            val share = expense.amount / expense.splitWith.size.coerceAtLeast(1)
            if (expense.paidBy != currentUserId && expense.splitWith.contains(currentUserId)) {
                owe += share
            } else if (expense.paidBy == currentUserId) {
                owed += expense.splitWith.filter { it != currentUserId }.size * share
            }
        }
        youOwe.postValue(owe)
        youAreOwed.postValue(owed)
        computeWeeklyTrend(expenses)

        val total = expenses.sumOf { it.amount }
        val byCategory = expenses.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }
        val paidByUidTotal = expenses.groupBy { it.paidBy }.mapValues { it.value.sumOf { e -> e.amount } }
        val nameMap = currentMembers.associate { it.userId to it.name }
        val currentAuthUser = authRepo.getCurrentUser()
        val topRaw = paidByUidTotal.maxByOrNull { it.value }
        val top = topRaw?.let { (uid, amount) ->
            val resolvedName = when {
                uid == currentUserId -> "You"
                !nameMap[uid].isNullOrBlank() -> nameMap[uid].orEmpty()
                currentMembers.any { it.name.equals(uid, ignoreCase = true) } -> uid
                uid == currentAuthUser?.uid && !currentAuthUser.displayName.isNullOrBlank() -> currentAuthUser.displayName.orEmpty()
                uid == currentAuthUser?.uid && !currentAuthUser.email.isNullOrBlank() ->
                    currentAuthUser.email.orEmpty().substringBefore("@")
                uid.contains("@") -> uid.substringBefore("@")
                uid.isBlank() && currentUserId.isNotBlank() -> "You"
                else -> null  // Don't save "Unknown" to Firestore
            }
            resolvedName?.let { name -> name to amount }
        }
        val memberCount = when {
            currentMembers.isNotEmpty() -> currentMembers.size
            trip.value?.memberIds?.isNotEmpty() == true -> trip.value!!.memberIds.size
            else -> expenses.flatMap { it.splitWith + it.paidBy }.filter { it.isNotBlank() }.toSet().size
        }
        val avg = if (memberCount > 0) total / memberCount else 0.0
        statsData.postValue(ExpenseStats(total, byCategory, top, avg))

        // Only persist summary when we have a valid top spender name
        if (top != null) {
            viewModelScope.launch {
                try {
                    expenseRepo.saveExpenseSummary(
                        tripId = tripId,
                        topSpenderId = topRaw?.key.orEmpty(),
                        topSpenderName = top.first,
                        topSpenderAmount = top.second,
                        totalSpent = total,
                        averagePerPerson = avg
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun computeWeeklyTrend(expenses: List<Expense>) {
        val now = System.currentTimeMillis()
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val currentWindowStart = now - weekMs
        val previousWindowStart = currentWindowStart - weekMs
        val currentWeek = expenses.filter { it.timestamp in currentWindowStart..now }
        val previousWeek = expenses.filter { it.timestamp in previousWindowStart until currentWindowStart }

        fun balances(list: List<Expense>): Pair<Double, Double> {
            var owe = 0.0
            var owed = 0.0
            list.forEach { e ->
                if (e.settled) return@forEach
                val share = e.amount / e.splitWith.size.coerceAtLeast(1)
                if (e.paidBy != currentUserId && e.splitWith.contains(currentUserId)) owe += share
                if (e.paidBy == currentUserId) owed += e.splitWith.filter { it != currentUserId }.size * share
            }
            return owe to owed
        }

        val (currOwe, currOwed) = balances(currentWeek)
        val (prevOwe, prevOwed) = balances(previousWeek)
        oweTrendPct.postValue(computePercentDelta(currOwe, prevOwe))
        owedTrendPct.postValue(computePercentDelta(currOwed, prevOwed))
    }

    private fun computePercentDelta(current: Double, previous: Double): Int {
        if (previous <= 0.0) {
            return if (current > 0.0) 100 else 0
        }
        return (((current - previous) / previous) * 100.0).toInt()
    }

    private fun buildItems(expenses: List<Expense>): List<ExpenseAdapter.ExpenseListItem> {
        if (expenses.isEmpty()) return emptyList()
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterday = today - 86_400_000L
        val displaySdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val keySdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return expenses.sortedByDescending { it.timestamp }
            .groupBy { keySdf.format(Date(it.timestamp)) }
            .flatMap { (_, dayExpenses) ->
                val ts = dayExpenses.first().timestamp
                val label = when {
                    ts >= today -> "TODAY"
                    ts >= yesterday -> "YESTERDAY"
                    else -> displaySdf.format(Date(ts)).uppercase()
                }
                listOf(ExpenseAdapter.ExpenseListItem.DateHeader(label)) +
                    dayExpenses.map { ExpenseAdapter.ExpenseListItem.ExpenseRow(it) }
            }
    }

    fun updateExpense(expense: Expense) {
        if (expense.id.isBlank()) return
        viewModelScope.launch {
            try {
                expenseRepo.updateExpense(tripId, expense.id, expense)
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    fun markExpenseSettled(expenseId: String) {
        if (expenseId.isBlank()) return
        val expense = allExpenses.firstOrNull { it.id == expenseId } ?: return
        val updated = expense.copy(settled = true)
        allExpenses = allExpenses.map { if (it.id == expenseId) updated else it }
        processExpenses(allExpenses, memberList = members.value)
        viewModelScope.launch {
            try {
                expenseRepo.markExpenseSettled(tripId, expenseId, true)
            } catch (e: Exception) {
                allExpenses = allExpenses.map { if (it.id == expenseId) expense else it }
                processExpenses(allExpenses, memberList = members.value)
                errorMessage.postValue(e.message ?: "Failed to mark settled")
            }
        }
    }

    fun refresh() {
        loadTripAndMembers()
        loadPersistedSummary()
        processExpenses(allExpenses, memberList = members.value)
    }

    private fun loadPersistedSummary() {
        viewModelScope.launch {
            val summary = expenseRepo.getExpenseSummary(tripId) ?: return@launch
            val current = statsData.value ?: ExpenseStats()
            val fallbackTop = if (summary.topSpenderName.isNotBlank()) {
                summary.topSpenderName to summary.topSpenderAmount
            } else {
                current.topSpender
            }
            statsData.postValue(
                current.copy(
                    total = if (current.total > 0.0) current.total else summary.totalSpent,
                    avgPerPerson = if (current.avgPerPerson > 0.0) current.avgPerPerson else summary.averagePerPerson,
                    topSpender = fallbackTop
                )
            )
        }
    }

    fun deleteExpense(expenseId: String) {
        if (expenseId.isBlank()) return
        viewModelScope.launch {
            try {
                expenseRepo.deleteExpense(tripId, expenseId)
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    fun addExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                expenseRepo.addExpense(tripId, expense)
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    fun getItemsForTab(tab: Int): LiveData<List<ExpenseAdapter.ExpenseListItem>> = when (tab) {
        0 -> tabAllItems
        1 -> tabMyItems
        2 -> tabSettledItems
        else -> tabAllItems
    }
}

