package com.manikandan.tripoo.ui.itinerary

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.manikandan.tripoo.data.model.ItineraryDay
import com.manikandan.tripoo.data.model.ItineraryStop
import com.manikandan.tripoo.data.repository.ItineraryRepository
import com.manikandan.tripoo.data.repository.TripRepository
import kotlinx.coroutines.launch

class ItineraryViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    val tripId: String = savedStateHandle.get<String>("tripId").orEmpty()
    private val repo = ItineraryRepository()
    private val tripRepo = TripRepository()

    private val _days = MutableLiveData<List<ItineraryDay>>(emptyList())
    val days: LiveData<List<ItineraryDay>> = _days
    val selectedIndex = MutableLiveData(0)
    val error = MutableLiveData<String?>()

    private var listener: ListenerRegistration? = null

    init {
        if (tripId.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val trip = tripRepo.getTrip(tripId)
                    if (trip != null) {
                        repo.ensureDays(tripId, trip.startDate, trip.endDate, trip.isOuting())
                    }
                } catch (e: Exception) {
                    error.postValue(e.message)
                }
            }
            listener = repo.listenToDays(tripId) { list, e ->
                if (e != null) {
                    error.postValue(e.message)
                    return@listenToDays
                }
                _days.postValue(list)
            }
        }
    }

    fun selectedDay(): ItineraryDay? {
        val list = _days.value.orEmpty()
        val i = selectedIndex.value ?: 0
        return list.getOrNull(i.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
    }

    fun addStop(stop: ItineraryStop) {
        val day = selectedDay() ?: return
        viewModelScope.launch {
            try {
                repo.addStop(tripId, day.id, stop)
            } catch (e: Exception) {
                error.postValue(e.message)
            }
        }
    }

    fun updateStop(stop: ItineraryStop) {
        val day = selectedDay() ?: return
        viewModelScope.launch {
            try {
                repo.updateStop(tripId, day.id, stop)
            } catch (e: Exception) {
                error.postValue(e.message)
            }
        }
    }

    fun deleteStop(stopId: String) {
        val day = selectedDay() ?: return
        viewModelScope.launch {
            try {
                repo.deleteStop(tripId, day.id, stopId)
            } catch (e: Exception) {
                error.postValue(e.message)
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
