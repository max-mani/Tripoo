package com.manikandan.tripoo.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Periodically checks tasks in the user's trips and shows a local notification when a due date has passed.
 * Runs on-device (no Cloud Scheduler / no Blaze Functions).
 */
class TripDeadlineWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db = FirebaseFirestore.getInstance()
        val userSnap = db.collection("users").document(uid).get().await()
        @Suppress("UNCHECKED_CAST")
        val tripIds = (userSnap.get("tripIds") as? List<String>)?.filter { it.isNotBlank() }.orEmpty()
        if (tripIds.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        for (tripId in tripIds) {
            val tripSnap = db.collection("trips").document(tripId).get().await()
            if (!tripSnap.exists()) continue
            val tripName = tripSnap.getString("name").orEmpty().ifBlank { "Trip" }

            val tasksSnap = db.collection("trips").document(tripId).collection("tasks").get().await()
            for (doc in tasksSnap.documents) {
                if (doc.getBoolean("completed") == true) continue
                if (doc.getBoolean("deadlineNotified") == true) continue
                val due = doc.getLong("dueDate") ?: continue
                if (due > now) continue

                val taskTitle = doc.getString("title").orEmpty().ifBlank { "Task" }
                val assigned = doc.getString("assignedTo").orEmpty()
                val notifyThisUser =
                    assigned.isEmpty() ||
                        assigned.equals("everyone", ignoreCase = true) ||
                        assigned == uid

                if (!notifyThisUser) continue

                if (!NotificationPrefs.areTripNotificationsEnabled(applicationContext)) continue

                LocalTripNotification.show(
                    applicationContext,
                    tripId,
                    tripName,
                    "Task due: $taskTitle",
                    ("deadline-" + tripId + "-" + doc.id).hashCode()
                )
                doc.reference.update("deadlineNotified", true).await()
            }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "trip_deadline_check"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<TripDeadlineWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
