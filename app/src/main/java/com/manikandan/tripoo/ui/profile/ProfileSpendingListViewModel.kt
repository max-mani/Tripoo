package com.manikandan.tripoo.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manikandan.tripoo.data.repository.AuthRepository
import com.manikandan.tripoo.data.repository.ExpenseRepository
import com.manikandan.tripoo.data.repository.TripRepository
import com.manikandan.tripoo.data.repository.UserRepository
import com.manikandan.tripoo.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TripSpendingRow(
    val tripId: String,
    val tripName: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val status: String,
    val memberCount: Int,
    val totalExpenses: Double,
)

class ProfileSpendingListViewModel : ViewModel() {

    private val auth = AuthRepository()
    private val userRepo = UserRepository()
    private val tripRepo = TripRepository()
    private val expenseRepo = ExpenseRepository()

    private val _rows = MutableLiveData<List<TripSpendingRow>>(emptyList())
    val rows: LiveData<List<TripSpendingRow>> = _rows

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun load() {
        val uid = auth.getCurrentUser()?.uid ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val list = withContext(Dispatchers.IO) {
                    val user = userRepo.getUser(uid) ?: return@withContext emptyList()
                    val ids = user.tripIds
                    if (ids.isEmpty()) return@withContext emptyList()
                    val trips = tripRepo.getTripsForUser(ids)
                    trips.map { trip ->
                        val total = expenseRepo.getTotalExpenses(trip.id)
                        val name = trip.name.ifBlank { trip.destination }.ifBlank { "Trip" }
                        TripSpendingRow(
                            tripId = trip.id,
                            tripName = name,
                            destination = trip.destination,
                            startDate = trip.startDate,
                            endDate = trip.endDate,
                            status = trip.status,
                            memberCount = trip.memberIds.size,
                            totalExpenses = total,
                        )
                    }.sortedByDescending { it.totalExpenses }
                }
                _rows.value = list
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun formatTotal(amount: Double): String =
        ProfileViewModel.formatInrFull(amount)
}
