package com.manikandan.tripoo.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.manikandan.tripoo.data.model.Task
import com.manikandan.tripoo.data.model.Trip
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.data.repository.TaskRepository
import com.manikandan.tripoo.data.repository.TripRepository
import com.manikandan.tripoo.ui.tasks.TaskAdapter
import kotlinx.coroutines.launch

class TasksViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    val tripId: String = savedStateHandle["tripId"] ?: ""

    private val tripRepo = TripRepository()
    private val taskRepo = TaskRepository()

    val trip = MutableLiveData<Trip?>()
    val members = MutableLiveData<List<TripMember>>(emptyList())

    // Raw task list — used by the fragment for search filtering
    val rawTasks = MutableLiveData<List<Task>>(emptyList())

    val allTaskItems = MutableLiveData<List<TaskAdapter.TaskItem>>(emptyList())
    val inProgressTasks = MutableLiveData<List<Task>>(emptyList())
    val completedTasks = MutableLiveData<List<Task>>(emptyList())

    val progressCompleted = MutableLiveData(0)
    val progressTotal = MutableLiveData(0)

    val errorMessage = MutableLiveData<String?>()
    val isLoading = MutableLiveData(true)

    init {
        if (tripId.isNotEmpty()) {
            loadTripAndMembers()
            collectTasks()
        }
    }

    private fun loadTripAndMembers() {
        viewModelScope.launch {
            try {
                trip.postValue(tripRepo.getTrip(tripId))
                members.postValue(tripRepo.getTripMembers(tripId))
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    private fun collectTasks() {
        viewModelScope.launch {
            try {
                taskRepo.listenToTasks(tripId).collect { tasks ->
                    isLoading.postValue(false)
                    rawTasks.postValue(tasks)
                    allTaskItems.postValue(TaskAdapter.buildItems(tasks))
                    // Safe cast: Firestore reflection can set Kotlin non-null Boolean to null at runtime;
                    // unboxing null Boolean with ! causes NPE without the safe cast.
                    inProgressTasks.postValue(tasks.filter { it.completed as? Boolean != true })
                    completedTasks.postValue(tasks.filter { it.completed as? Boolean == true })
                    progressCompleted.postValue(tasks.count { it.completed as? Boolean == true })
                    progressTotal.postValue(tasks.size)
                }
            } catch (e: Exception) {
                isLoading.postValue(false)
                errorMessage.postValue(e.message)
            }
        }
    }

    /** Re-fetches trip info and members (tasks update via real-time Flow). */
    fun refresh() {
        viewModelScope.launch {
            try {
                trip.postValue(tripRepo.getTrip(tripId))
                members.postValue(tripRepo.getTripMembers(tripId))
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    fun toggleTask(task: Task) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        viewModelScope.launch {
            if (uid.isEmpty() || !tripRepo.canUserManageTripAsLeader(tripId, uid)) return@launch
            try {
                val currentCompleted = task.completed as? Boolean ?: false
                taskRepo.updateTaskCompletion(tripId, task.id, !currentCompleted)
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            try {
                taskRepo.deleteTask(tripId, task.id)
            } catch (e: Exception) {
                errorMessage.postValue(e.message)
            }
        }
    }

    fun buildItems(tasks: List<Task>): List<TaskAdapter.TaskItem> = TaskAdapter.buildItems(tasks)
}
