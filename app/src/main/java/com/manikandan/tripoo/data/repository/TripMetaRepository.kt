package com.manikandan.tripoo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.manikandan.tripoo.data.model.TripNotes
import kotlinx.coroutines.tasks.await

class TripMetaRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun notesRef(tripId: String) =
        db.collection("trips").document(tripId).collection("meta").document("notes")

    fun listenToNotes(tripId: String, callback: (TripNotes, Exception?) -> Unit): ListenerRegistration {
        return notesRef(tripId).addSnapshotListener { snap, e ->
            if (e != null) {
                callback(TripNotes(), e)
                return@addSnapshotListener
            }
            val data = snap?.data.orEmpty()
            callback(
                TripNotes(
                    text = data["text"] as? String ?: "",
                    updatedBy = data["updatedBy"] as? String ?: "",
                    updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                ),
                null
            )
        }
    }

    suspend fun saveNotes(tripId: String, text: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        notesRef(tripId).set(
            mapOf(
                "text" to text,
                "updatedBy" to uid,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }
}
