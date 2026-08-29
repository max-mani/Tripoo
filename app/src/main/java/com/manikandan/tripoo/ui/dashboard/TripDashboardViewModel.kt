package com.manikandan.tripoo.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manikandan.tripoo.data.model.TripWithMeta
import com.manikandan.tripoo.data.model.User
import com.manikandan.tripoo.data.repository.AuthRepository
import com.manikandan.tripoo.data.repository.ExpenseRepository
import com.manikandan.tripoo.data.repository.TripRepository
import com.manikandan.tripoo.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TripDashboardViewModel : ViewModel() {
    private val auth = AuthRepository()
    private val userRepo = UserRepository()
    private val tripRepo = TripRepository()
    private val expenseRepo = ExpenseRepository()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _allTrips = MutableLiveData<List<TripWithMeta>>(emptyList())
    val allTrips: LiveData<List<TripWithMeta>> = _allTrips

    private val _filteredTrips = MutableLiveData<List<TripWithMeta>>(emptyList())
    val filteredTrips: LiveData<List<TripWithMeta>> = _filteredTrips

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private var currentFilter = "all"

    fun loadTrips() {
        val uid = auth.getCurrentUser()?.uid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val user = withContext(Dispatchers.IO) { userRepo.getUser(uid) }
                _user.postValue(user)
                val tripIds = user?.tripIds ?: emptyList()
                val trips = withContext(Dispatchers.IO) { tripRepo.getTripsForUser(tripIds) }
                val withMeta = withContext(Dispatchers.IO) {
                    coroutineScope {
                        trips.map { trip ->
                            async {
                                val leader = tripRepo.canUserManageTripAsLeader(trip.id, uid)
                                val totalSpent = expenseRepo.getTotalExpenses(trip.id)
                                TripWithMeta(
                                    trip = trip,
                                    memberCount = trip.memberIds.size,
                                    userRole = if (leader) "admin" else "member",
                                    totalSpent = totalSpent
                                )
                            }
                        }.map { it.await() }
                    }
                }
                _allTrips.postValue(withMeta)
                applyFilter(currentFilter, withMeta)
            } catch (e: Exception) {
                _errorMessage.postValue(e.message)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun setFilter(filter: String) {
        currentFilter = filter
        applyFilter(filter, _allTrips.value ?: emptyList())
    }

    private fun applyFilter(filter: String, trips: List<TripWithMeta>) {
        _filteredTrips.postValue(when (filter) {
            "active" -> trips.filter { it.trip.status == "active" }
            "upcoming" -> trips.filter { it.trip.status == "upcoming" }
            "past" -> trips.filter { it.trip.status == "past" }
            "outing" -> trips.filter { it.trip.isOuting() }
            else -> trips
        })
    }

    fun getCurrentUserName(): String {
        val firestoreName = _user.value?.name?.takeIf { it.isNotBlank() }
        return firestoreName ?: (auth.getCurrentUser()?.displayName ?: "there")
    }

    fun getCurrentUserInitials(): String {
        val name = getCurrentUserName()
        val parts = name.trim().split(" ")
        return if (parts.size >= 2) "${parts.first().first()}${parts.last().first()}".uppercase()
        else name.take(2).uppercase().ifEmpty { "?" }
    }

    fun clearErrorMessage() { _errorMessage.value = null }
}
