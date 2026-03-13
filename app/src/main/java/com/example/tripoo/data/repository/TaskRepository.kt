package com.example.tripoo.data.repository

import com.example.tripoo.data.model.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TaskRepository {
    private val db = FirebaseFirestore.getInstance()

    suspend fun addTask(tripId: String, task: Task) {
        try {
            val ref = db.collection("trips").document(tripId).collection("tasks").document()
            ref.set(task.copy(id = ref.id)).await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun listenToTasks(tripId: String): Flow<List<Task>> = callbackFlow {
        val listener = db.collection("trips").document(tripId).collection("tasks")
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull {
                    it.toObject(Task::class.java)?.copy(id = it.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateTaskCompletion(tripId: String, taskId: String, completed: Boolean) {
        try {
            db.collection("trips").document(tripId).collection("tasks")
                .document(taskId).update("completed", completed).await()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun getTaskProgress(tripId: String): Pair<Int, Int> {
        return try {
            val tasks = db.collection("trips").document(tripId).collection("tasks")
                .get().await().documents.mapNotNull { it.toObject(Task::class.java) }
            Pair(tasks.count { it.completed }, tasks.size)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    fun listenToTasks(tripId: String, callback: (List<Task>, Exception?) -> Unit): ListenerRegistration {
        return db.collection("trips").document(tripId).collection("tasks")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    callback(emptyList(), e)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Task::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                callback(list, null)
            }
    }

    fun addTask(tripId: String, task: Task, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { addTask(tripId, task) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    suspend fun updateTask(tripId: String, taskId: String, task: Task) {
        try {
            val updates = mapOf(
                "title" to task.title,
                "category" to task.category,
                "assignedTo" to task.assignedTo,
                "completed" to task.completed,
                "dueDate" to task.dueDate,
                "priority" to task.priority,
                "notes" to task.notes
            )
            db.collection("trips").document(tripId).collection("tasks").document(taskId).update(updates).await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun updateTask(tripId: String, taskId: String, task: Task, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { updateTask(tripId, taskId, task) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    suspend fun deleteTask(tripId: String, taskId: String) {
        try {
            db.collection("trips").document(tripId).collection("tasks").document(taskId).delete().await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun deleteTask(tripId: String, taskId: String, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { deleteTask(tripId, taskId) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    fun updateTaskCompletion(tripId: String, taskId: String, completed: Boolean, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { updateTaskCompletion(tripId, taskId, completed) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }
}
