package com.manikandan.tripoo.notifications

import android.content.Context
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.ArrayDeque

/**
 * Listens to [users.tripIds] and, for each trip, to [trips/{id}/fanoutNotifications] so members
 * receive local notifications while the app can sync with Firestore (no FCM / no Blaze Functions).
 */
object FanoutTripNotificationListener {

    private var appContext: Context? = null
    private var userListener: ListenerRegistration? = null
    private val tripListeners = mutableMapOf<String, ListenerRegistration>()
    private var activeUid: String? = null

    private val seenDocKeys = ArrayDeque<String>()
    private const val MAX_SEEN = 400

    private val db = FirebaseFirestore.getInstance()

    @Synchronized
    fun start(context: Context, uid: String) {
        if (uid == activeUid && userListener != null) return
        stop()
        activeUid = uid
        appContext = context.applicationContext
        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                val tripIds = (snap?.get("tripIds") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    .orEmpty()
                syncTripListeners(tripIds, uid)
            }
    }

    @Synchronized
    fun stop() {
        userListener?.remove()
        userListener = null
        tripListeners.values.forEach { it.remove() }
        tripListeners.clear()
        activeUid = null
        appContext = null
        seenDocKeys.clear()
    }

    private fun rememberSeen(tripId: String, docId: String) {
        val key = "$tripId/$docId"
        if (seenDocKeys.contains(key)) return
        while (seenDocKeys.size >= MAX_SEEN) {
            seenDocKeys.removeFirst()
        }
        seenDocKeys.addLast(key)
    }

    private fun isSeen(tripId: String, docId: String): Boolean =
        seenDocKeys.contains("$tripId/$docId")

    private fun syncTripListeners(tripIds: List<String>, uid: String) {
        val ctx = appContext ?: return
        val keep = tripIds.toSet()
        val toRemove = tripListeners.keys - keep
        toRemove.forEach { tid -> tripListeners.remove(tid)?.remove() }
        for (tid in tripIds) {
            if (tripListeners.containsKey(tid)) continue
            tripListeners[tid] = attachTripListener(ctx, tid, uid)
        }
    }

    private fun attachTripListener(ctx: Context, tripId: String, uid: String): ListenerRegistration {
        var initial = true
        return db.collection("trips").document(tripId).collection("fanoutNotifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
            .addSnapshotListener { qs, err ->
                if (err != null || qs == null) return@addSnapshotListener
                if (initial) {
                    initial = false
                    qs.documents.forEach { rememberSeen(tripId, it.id) }
                    return@addSnapshotListener
                }
                for (change in qs.documentChanges) {
                    if (change.type != DocumentChange.Type.ADDED) continue
                    val doc = change.document
                    if (doc.metadata.hasPendingWrites()) continue
                    if (isSeen(tripId, doc.id)) continue
                    rememberSeen(tripId, doc.id)
                    val actor = doc.getString("actorUid").orEmpty()
                    if (actor == uid) continue
                    val title = doc.getString("title") ?: continue
                    val body = doc.getString("body").orEmpty()
                    if (!NotificationPrefs.areTripNotificationsEnabled(ctx)) continue
                    LocalTripNotification.show(ctx, tripId, title, body, doc.id.hashCode())
                }
            }
    }
}
