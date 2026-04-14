package com.manikandan.tripoo.notifications

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Writes one document under the trip so all members' apps (listening on Spark / no Cloud Functions)
 * can show a local notification. Not FCM — no server or device tokens required.
 */
object FanoutNotificationPublisher {

    @JvmStatic
    fun publishAsync(tripId: String, title: String, body: String, type: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                publish(tripId, title, body, type)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun publish(tripId: String, title: String, body: String, type: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val data = hashMapOf(
            "title" to title,
            "body" to body,
            "type" to type,
            "actorUid" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance()
            .collection("trips").document(tripId)
            .collection("fanoutNotifications").document()
            .set(data)
            .await()
    }
}
