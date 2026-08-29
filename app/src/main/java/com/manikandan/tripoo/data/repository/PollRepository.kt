package com.manikandan.tripoo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.manikandan.tripoo.data.model.Poll
import com.manikandan.tripoo.notifications.FanoutNotificationPublisher
import com.manikandan.tripoo.notifications.FanoutTypes
import kotlinx.coroutines.tasks.await

class PollRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun col(tripId: String) =
        db.collection("trips").document(tripId).collection("polls")

    fun listenToPolls(tripId: String, callback: (List<Poll>, Exception?) -> Unit): ListenerRegistration {
        return col(tripId).addSnapshotListener { snap, e ->
            if (e != null) {
                callback(emptyList(), e)
                return@addSnapshotListener
            }
            val list = snap?.documents?.map { parsePoll(it.id, it.data.orEmpty()) }.orEmpty()
                .sortedWith(compareBy({ it.closed }, { -it.createdAt }))
            callback(list, null)
        }
    }

    suspend fun createPoll(tripId: String, question: String, options: List<String>) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val ref = col(tripId).document()
        ref.set(
            mapOf(
                "question" to question.trim(),
                "options" to options.map { it.trim() }.filter { it.isNotEmpty() },
                "votes" to emptyMap<String, Int>(),
                "createdBy" to uid,
                "createdAt" to System.currentTimeMillis(),
                "closed" to false
            )
        ).await()
        FanoutNotificationPublisher.publishAsync(
            tripId, "Poll", "New poll: ${question.trim()}", FanoutTypes.POLL_CREATED
        )
    }

    suspend fun vote(tripId: String, pollId: String, optionIndex: Int) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        col(tripId).document(pollId).update("votes.$uid", optionIndex).await()
    }

    suspend fun setClosed(tripId: String, pollId: String, closed: Boolean) {
        col(tripId).document(pollId).update("closed", closed).await()
        if (closed) {
            FanoutNotificationPublisher.publishAsync(
                tripId, "Poll", "A poll was closed", FanoutTypes.POLL_CLOSED
            )
        }
    }

    suspend fun deletePoll(tripId: String, pollId: String) {
        col(tripId).document(pollId).delete().await()
    }

    companion object {
        fun parsePoll(id: String, data: Map<String, Any>): Poll {
            val options = (data["options"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val votesRaw = data["votes"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val votes = votesRaw.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val idx = (v as? Number)?.toInt() ?: return@mapNotNull null
                key to idx
            }.toMap()
            return Poll(
                id = id,
                question = data["question"] as? String ?: "",
                options = options,
                votes = votes,
                createdBy = data["createdBy"] as? String ?: "",
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                closed = data["closed"] as? Boolean ?: false
            )
        }
    }
}
