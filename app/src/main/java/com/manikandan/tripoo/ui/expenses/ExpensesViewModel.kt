package com.manikandan.tripoo.ui.expenses

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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExpenseFilter { ALL, MY_SPENDING, SETTLED, STATS }

class ExpensesViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val tripId: String = savedStateHandle.get<String>("tripId") ?: ""
    private val auth = AuthRepository()
    private val expenseRepo = ExpenseRepository()
    private val tripRepo = TripRepository()

    val trip = MutableLiveData<Trip?>()
    val expenseItems = MutableLiveData<List<ExpenseAdapter.ExpenseListItem>>()
    val youOwe = MutableLiveData(0.0)
    val youAreOwed = MutableLiveData(0.0)
    val members = MutableLiveData<List<TripMember>>()
    val errorMessage = MutableLiveData<String?>()

    val currentUserId: String
        get() = auth.getCurrentUser()?.uid ?: ""

    private var allExpenses: List<Expense> = emptyList()
    private var currentFilter: ExpenseFilter = ExpenseFilter.ALL

    init {
        loadTrip()
        loadMembers()
        collectExpenses()
    }

    private fun loadTrip() {
        viewModelScope.launch {
            try {
                val t = tripRepo.getTrip(tripId)
                trip.postValue(t)
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    private fun loadMembers() {
        viewModelScope.launch {
            try {
                val list = tripRepo.getTripMembers(tripId)
                members.postValue(list)
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    private fun collectExpenses() {
        viewModelScope.launch {
            try {
                val sdf = SimpleDateFormat("MMMM d", Locale.getDefault())
                val daySdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

                expenseRepo.listenToExpenses(tripId).collect { expenses ->
                    allExpenses = expenses
                    applyFilter(currentFilter, sdf, daySdf)

                    val (owe, owed) = expenseRepo.calculateBalances(tripId, currentUserId)
                    youOwe.postValue(owe)
                    youAreOwed.postValue(owed)
                }
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    fun setFilter(filter: ExpenseFilter) {
        currentFilter = filter
        val sdf = SimpleDateFormat("MMMM d", Locale.getDefault())
        val daySdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        applyFilter(filter, sdf, daySdf)
    }

    private fun applyFilter(
        filter: ExpenseFilter,
        sdf: SimpleDateFormat,
        daySdf: SimpleDateFormat
    ) {
        val filtered = when (filter) {
            ExpenseFilter.ALL -> allExpenses
            ExpenseFilter.MY_SPENDING ->
                allExpenses.filter { e ->
                    e.paidBy != currentUserId && e.splitWith.contains(currentUserId)
                }
            ExpenseFilter.SETTLED ->
                allExpenses.filter { e ->
                    e.paidBy == currentUserId && !e.splitWith.contains(currentUserId)
                }
            ExpenseFilter.STATS ->
                allExpenses
        }

        val grouped = filtered.groupBy { daySdf.format(Date(it.timestamp)) }
        val items = mutableListOf<ExpenseAdapter.ExpenseListItem>()

        grouped.toSortedMap(compareByDescending { it }).forEach { (_, dayExpenses) ->
            val headerLabel = if (items.isEmpty() && filter == ExpenseFilter.ALL) {
                "Recent Activity"
            } else {
                sdf.format(Date(dayExpenses.first().timestamp))
            }
            items.add(ExpenseAdapter.ExpenseListItem.DateHeader(headerLabel))
            dayExpenses.forEach { exp ->
                items.add(ExpenseAdapter.ExpenseListItem.ExpenseRow(exp))
            }
        }

        expenseItems.postValue(items)
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
}

