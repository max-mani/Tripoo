package com.manikandan.tripoo.data.repository

import com.manikandan.tripoo.data.model.RecentCollaborator
import com.manikandan.tripoo.data.model.User
import com.manikandan.tripoo.utils.UserAvatarIdentity
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
            // Never include recentCollaborators here — a signup/merge User() defaults
            // to emptyList and would wipe recents on SetOptions.merge().
            val payload = hashMapOf<String, Any?>(
                "uid" to user.uid,
                "name" to user.name,
                "email" to user.email,
                "phoneNumber" to user.phoneNumber,
                "preferredLanguage" to user.preferredLanguage,
                "preferredCurrency" to user.preferredCurrency,
                "photoUrl" to user.photoUrl,
                "tripIds" to user.tripIds,
                "lastActiveTripId" to user.lastActiveTripId,
                "avatarLetter" to user.avatarLetter,
                "avatarColorHex" to user.avatarColorHex
            )
            users.document(user.uid).set(payload, SetOptions.merge()).await()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun getUser(uid: String): User? {
        return try {
            val snap = users.document(uid).get().await()
            val user = snap.toObject(User::class.java) ?: return null
            user.copy(
                uid = user.uid.ifBlank { snap.id },
                recentCollaborators = parseRecentCollaborators(snap.get("recentCollaborators"))
            )
        } catch (e: Exception) {
            null
        }
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

    suspend fun deleteUserDocument(uid: String) {
        users.document(uid).delete().await()
    }

    suspend fun updatePhoneNumber(uid: String, phone: String) {
        users.document(uid).update("phoneNumber", phone.trim()).await()
    }

    suspend fun updateDocumentEmail(uid: String, email: String) {
        users.document(uid).update("email", email.trim()).await()
    }

    suspend fun updatePreferences(uid: String, language: String?, currency: String?) {
        val map = buildMap<String, Any> {
            language?.let { put("preferredLanguage", it) }
            currency?.let { put("preferredCurrency", it) }
        }
        if (map.isNotEmpty()) {
            users.document(uid).update(map).await()
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

    /**
     * Read-modify-write this user's [User.recentCollaborators] only (own doc).
     * Dedupes by uid, refreshes lastSeenAt, caps at [RECENT_CAP].
     */
    suspend fun mergeRecentCollaborators(uid: String, newOnes: List<RecentCollaborator>) {
        if (uid.isBlank() || newOnes.none { it.uid.isNotBlank() }) return
        try {
            db.runTransaction { tx ->
                val ref = users.document(uid)
                val snap = tx.get(ref)
                val existing = parseRecentCollaborators(snap.get("recentCollaborators"))
                val incoming = newOnes.filter { it.uid.isNotBlank() && it.uid != uid }
                if (incoming.isEmpty()) return@runTransaction null
                val now = System.currentTimeMillis()
                if (shouldSkipRecentWrite(existing, incoming, now)) return@runTransaction null
                val merged = mergeCollaboratorLists(existing, incoming, now, RECENT_CAP)
                tx.update(ref, "recentCollaborators", merged.map { collaboratorToMap(it) })
                null
            }.await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun mergeRecentCollaborators(uid: String, newOnes: List<RecentCollaborator>, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { mergeRecentCollaborators(uid, newOnes) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
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
            val trimmed = name.trim()
            val letter = UserAvatarIdentity.letterFromName(trimmed)
            val updates = mutableMapOf<String, Any?>(
                "name" to trimmed,
                "photoUrl" to (photoUrl ?: ""),
                "avatarLetter" to letter
            )
            val blankPhoto = photoUrl.isNullOrBlank()
            if (blankPhoto) {
                val existing = getUser(uid)
                if (existing?.avatarColorHex.isNullOrBlank()) {
                    updates["avatarColorHex"] = UserAvatarIdentity.bgForSeed(uid)
                }
            }
            users.document(uid).update(updates as Map<String, Any>).await()
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * When the user has no photo, ensure [User.avatarLetter] and [User.avatarColorHex] exist in Firestore.
     * Returns the resolved letter and background color for UI.
     */
    suspend fun ensureAvatarIdentityFields(uid: String): Pair<String, String> {
        val user = getUser(uid) ?: return Pair(UserAvatarIdentity.letterFromName(""), UserAvatarIdentity.bgForSeed(uid))
        val photoEmpty = user.photoUrl.isNullOrBlank()
        if (!photoEmpty) {
            return Pair(
                user.avatarLetter?.ifBlank { UserAvatarIdentity.letterFromName(user.name) }
                    ?: UserAvatarIdentity.letterFromName(user.name),
                user.avatarColorHex ?: UserAvatarIdentity.bgForSeed(uid)
            )
        }
        var letter = user.avatarLetter?.trim()?.take(1)?.uppercase()
        if (letter.isNullOrEmpty()) {
            letter = UserAvatarIdentity.letterFromName(user.name.ifBlank { user.email.substringBefore("@") })
        }
        var color = user.avatarColorHex?.trim()
        if (color.isNullOrEmpty()) {
            color = UserAvatarIdentity.bgForSeed(uid)
        }
        if (user.avatarLetter.isNullOrBlank() || user.avatarColorHex.isNullOrBlank()) {
            users.document(uid).update(
                mapOf(
                    "avatarLetter" to letter,
                    "avatarColorHex" to color
                )
            ).await()
        }
        return Pair(letter, color)
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

    companion object {
        const val RECENT_CAP = 20
        const val SKIP_IF_SEEN_WITHIN_MS = 60L * 60L * 1000L

        fun parseRecentCollaborators(raw: Any?): List<RecentCollaborator> {
            val list = raw as? List<*> ?: return emptyList()
            return list.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val id = m["uid"] as? String ?: return@mapNotNull null
                if (id.isBlank()) return@mapNotNull null
                RecentCollaborator(
                    uid = id,
                    name = m["name"] as? String ?: "",
                    photoUrl = m["photoUrl"] as? String,
                    lastSeenAt = (m["lastSeenAt"] as? Number)?.toLong() ?: 0L
                )
            }
        }

        fun mergeCollaboratorLists(
            existing: List<RecentCollaborator>,
            incoming: List<RecentCollaborator>,
            now: Long,
            cap: Int = RECENT_CAP
        ): List<RecentCollaborator> {
            val byId = existing.filter { it.uid.isNotBlank() }.associateBy { it.uid }.toMutableMap()
            for (n in incoming) {
                if (n.uid.isBlank()) continue
                val prev = byId[n.uid]
                byId[n.uid] = RecentCollaborator(
                    uid = n.uid,
                    name = n.name.ifBlank { prev?.name.orEmpty() },
                    photoUrl = n.photoUrl?.takeIf { it.isNotBlank() } ?: prev?.photoUrl,
                    lastSeenAt = now
                )
            }
            return byId.values.sortedByDescending { it.lastSeenAt }.take(cap)
        }

        fun shouldSkipRecentWrite(
            existing: List<RecentCollaborator>,
            incoming: List<RecentCollaborator>,
            now: Long
        ): Boolean {
            if (incoming.isEmpty()) return true
            val byId = existing.associateBy { it.uid }
            return incoming.all { n ->
                val prev = byId[n.uid] ?: return@all false
                now - prev.lastSeenAt < SKIP_IF_SEEN_WITHIN_MS
            }
        }

        fun collaboratorToMap(c: RecentCollaborator): Map<String, Any> {
            val m = mutableMapOf<String, Any>(
                "uid" to c.uid,
                "name" to c.name,
                "lastSeenAt" to c.lastSeenAt
            )
            c.photoUrl?.let { if (it.isNotBlank()) m["photoUrl"] = it }
            return m
        }
    }
}
