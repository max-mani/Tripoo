package com.manikandan.tripoo.ui.polls

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.manikandan.tripoo.data.model.Poll
import com.manikandan.tripoo.data.repository.PollRepository
import com.manikandan.tripoo.data.repository.TripRepository
import kotlinx.coroutines.launch

class PollsViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    val tripId: String = savedStateHandle.get<String>("tripId").orEmpty()
    private val repo = PollRepository()
    private val tripRepo = TripRepository()

    private val _polls = MutableLiveData<List<Poll>>(emptyList())
    val polls: LiveData<List<Poll>> = _polls
    val isLeader = MutableLiveData(false)
    val error = MutableLiveData<String?>()

    private var listener: ListenerRegistration? = null

    init {
        if (tripId.isNotBlank()) {
            listener = repo.listenToPolls(tripId) { list, e ->
                if (e != null) error.postValue(e.message) else _polls.postValue(list)
            }
            viewModelScope.launch {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                isLeader.postValue(tripRepo.canUserManageTripAsLeader(tripId, uid))
            }
        }
    }

    fun create(question: String, options: List<String>) {
        viewModelScope.launch {
            try {
                repo.createPoll(tripId, question, options)
            } catch (e: Exception) {
                error.postValue(e.message)
            }
        }
    }

    fun vote(pollId: String, index: Int) {
        viewModelScope.launch {
            try {
                repo.vote(tripId, pollId, index)
            } catch (e: Exception) {
                error.postValue(e.message)
            }
        }
    }

    fun close(pollId: String) {
        viewModelScope.launch {
            try {
                repo.setClosed(tripId, pollId, true)
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
