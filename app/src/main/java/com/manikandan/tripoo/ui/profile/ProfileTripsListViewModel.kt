package com.manikandan.tripoo.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manikandan.tripoo.data.model.TripWithMeta
import com.manikandan.tripoo.data.repository.AuthRepository
import com.manikandan.tripoo.data.repository.ExpenseRepository
import com.manikandan.tripoo.data.repository.TripRepository
import com.manikandan.tripoo.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileTripsListViewModel : ViewModel() {

    private val auth = AuthRepository()
    private val userRepo = UserRepository()
    private val tripRepo = TripRepository()
    private val expenseRepo = ExpenseRepository()

    private val _trips = MutableLiveData<List<TripWithMeta>>(emptyList())
    val trips: LiveData<List<TripWithMeta>> = _trips

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
                        val spent = expenseRepo.getTotalExpenses(trip.id)
                        TripWithMeta(
                            trip = trip,
                            memberCount = trip.memberIds.size,
                            userRole = "member",
                            totalSpent = spent,
                        )
                    }
                }
                _trips.value = list
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }
}
