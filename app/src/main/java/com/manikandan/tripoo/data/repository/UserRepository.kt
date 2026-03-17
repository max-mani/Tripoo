package com.manikandan.tripoo.data.repository

import com.manikandan.tripoo.data.model.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserRepository {
    private val db = FirebaseFirestore.getInstance()
    private val users = db.collection("users")

    suspend fun createOrUpdateUser(user: User) {
        try {
            users.document(user.uid).set(user, SetOptions.merge()).await()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun getUser(uid: String): User? =
        try {
            users.document(uid).get().await().toObject(User::class.java)
        } catch (e: Exception) {
            null
        }

    suspend fun addTripToUser(uid: String, tripId: String) {
        try {
            users.document(uid).update(
                "tripIds", FieldValue.arrayUnion(tripId),
                "lastActiveTripId", tripId
            ).await()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun removeTripFromUser(uid: String, tripId: String) {
        try {
            db.runTransaction { tx ->
                val doc = tx.get(users.document(uid))
                val currentIds = doc.get("tripIds") as? List<String> ?: emptyList()
                val newIds = currentIds.filter { it != tripId }
                val newLastActive = newIds.lastOrNull()
                tx.update(users.document(uid), mapOf(
                    "tripIds" to newIds,
                    "lastActiveTripId" to newLastActive
                ))
            }.await()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun setLastActiveTrip(uid: String, tripId: String) {
        try {
            users.document(uid).update("lastActiveTripId", tripId).await()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun updateProfile(uid: String, name: String, photoUrl: String?) {
        try {
            val updates = mutableMapOf<String, Any?>(
                "name" to name,
                "photoUrl" to (photoUrl ?: "")
            )
            users.document(uid).update(updates as Map<String, Any>).await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun getUser(uid: String, callback: (User?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val user = withContext(Dispatchers.IO) { getUser(uid) }
            callback(user)
        }
    }

    fun createOrUpdateUser(user: User, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { createOrUpdateUser(user) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    fun updateProfile(uid: String, name: String, photoUrl: String?, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { updateProfile(uid, name, photoUrl) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    fun addTripToUser(uid: String, tripId: String, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { addTripToUser(uid, tripId) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    fun removeTripFromUser(uid: String, tripId: String, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { removeTripFromUser(uid, tripId) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }
}
