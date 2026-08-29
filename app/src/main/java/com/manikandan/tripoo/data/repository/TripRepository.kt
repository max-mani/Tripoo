package com.manikandan.tripoo.data.repository

import com.manikandan.tripoo.data.model.LeaveTripResult
import com.manikandan.tripoo.data.model.Trip
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.data.model.User
import com.manikandan.tripoo.notifications.FanoutNotificationPublisher
import com.manikandan.tripoo.notifications.FanoutTypes
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TripRepository {
    private val db = FirebaseFirestore.getInstance()
    private val trips = db.collection("trips")
    private val userRepository = UserRepository()

    /**
     * Kotlin/Firestore sometimes maps [TripMember.isAdmin] to a field named "admin" on write.
     * Reads must accept both "isAdmin" and "admin".
     */
    private fun parseTripMember(doc: DocumentSnapshot): TripMember? {
        val m = doc.toObject(TripMember::class.java) ?: return null
        val adminFlag = doc.getBoolean("isAdmin") ?: doc.getBoolean("admin") ?: m.isAdmin
        return m.copy(userId = doc.id, isAdmin = adminFlag)
    }
    private val memberEnrichScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var memberEnrichJob: Job? = null

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return "TRP-" + (1..3).map { chars.random() }.joinToString("")
    }

    private fun deriveStatus(startDate: Long, endDate: Long): String {
        val now = System.currentTimeMillis()
        return when {
            now < startDate -> "upcoming"
            now > endDate -> "past"
            else -> "active"
        }
    }

    suspend fun createTrip(trip: Trip, adminMember: TripMember): String? {
        return try {
            val docRef = trips.document()
            val joinCode = generateJoinCode()
            val status = deriveStatus(trip.startDate, trip.endDate)
            val newTrip = trip.copy(
                id = docRef.id,
                joinCode = joinCode,
                memberIds = listOf(adminMember.userId),
                status = status
            )
            val batch = db.batch()
            batch.set(docRef, newTrip)
            batch.set(docRef.collection("members").document(adminMember.userId), adminMember)
            batch.commit().await()
            docRef.id
        } catch (e: Exception) {
            null
        }
    }

    suspend fun joinTrip(joinCode: String, member: TripMember): String? {
        return try {
            val snapshot = trips.whereEqualTo("joinCode", joinCode).limit(1).get().await()
            if (snapshot.isEmpty) return null
            val tripDoc = snapshot.documents[0]
            val tripId = tripDoc.id
            val batch = db.batch()
            batch.set(tripDoc.reference.collection("members").document(member.userId), member)
            batch.update(tripDoc.reference, "memberIds", FieldValue.arrayUnion(member.userId))
            batch.commit().await()
            val tripName = tripDoc.toObject(Trip::class.java)?.name?.trim().orEmpty().ifBlank { "Trip" }
            FanoutNotificationPublisher.publishAsync(
                tripId,
                tripName,
                "${member.name} joined the trip",
                FanoutTypes.MEMBER_JOINED
            )
            tripId
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTripsForUser(tripIds: List<String>): List<Trip> {
        if (tripIds.isEmpty()) return emptyList()
        return try {
            tripIds.chunked(10).flatMap { chunk ->
                trips.whereIn(FieldPath.documentId(), chunk).get().await()
                    .documents.mapNotNull { doc ->
                        doc.toObject(Trip::class.java)?.copy(id = doc.id)
                    }
            }.sortedBy { deriveStatus(it.startDate, it.endDate).let { s ->
                when (s) {
                    "active" -> 0
                    "upcoming" -> 1
                    else -> 2
                }
            } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTrip(tripId: String): Trip? =
        try {
            trips.document(tripId).get().await()
                .toObject(Trip::class.java)?.copy(id = tripId)
        } catch (e: Exception) {
            null
        }

    /**
     * Trip organiser ([Trip.adminId]) or a member with [TripMember.isAdmin] (co-organiser)
     * can edit trip details, delete the trip, and perform organiser-level actions in the app.
     */
    suspend fun canUserManageTripAsLeader(tripId: String, uid: String): Boolean {
        if (uid.isEmpty()) return false
        val trip = getTrip(tripId) ?: return false
        if (trip.adminId == uid) return true
        val snap = trips.document(tripId).collection("members").document(uid).get().await()
        if (!snap.exists()) return false
        return parseTripMember(snap)?.isAdmin == true
    }

    fun canUserManageTripAsLeader(tripId: String, uid: String, callback: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val ok = withContext(Dispatchers.IO) { canUserManageTripAsLeader(tripId, uid) }
            callback(ok)
        }
    }

    private suspend fun updateTripDetailsSuspend(
        tripId: String,
        name: String,
        destination: String,
        description: String,
        startDateMs: Long,
        endDateMs: Long,
        budget: Double
    ) {
        val status = deriveStatus(startDateMs, endDateMs)
        trips.document(tripId).update(
            mapOf(
                "name" to name,
                "destination" to destination,
                "description" to description,
                "startDate" to startDateMs,
                "endDate" to endDateMs,
                "budget" to budget,
                "status" to status
            )
        ).await()
        FanoutNotificationPublisher.publishAsync(
            tripId,
            name.ifBlank { "Trip" },
            "Trip details were updated",
            FanoutTypes.TRIP_EDITED
        )
    }

    fun updateTripDetails(
        tripId: String,
        name: String,
        destination: String,
        description: String,
        startDateMs: Long,
        endDateMs: Long,
        budget: Double,
        callback: (Throwable?) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    updateTripDetailsSuspend(tripId, name, destination, description, startDateMs, endDateMs, budget)
                }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    suspend fun getTripMembers(tripId: String): List<TripMember> {
        return try {
            val memberDocs = trips.document(tripId).collection("members").get().await()
                .documents.mapNotNull { doc -> parseTripMember(doc) }
            if (memberDocs.isEmpty()) emptyList()
            else enrichMembersWithUserProfiles(memberDocs)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun enrichMembersWithUserProfiles(members: List<TripMember>): List<TripMember> {
        if (members.isEmpty()) return members
        val ids = members.map { it.userId }.filter { it.isNotEmpty() }.distinct()
        val userById = HashMap<String, User?>()
        ids.chunked(10).forEach { chunk ->
            try {
                db.collection("users").whereIn(FieldPath.documentId(), chunk).get().await()
                    .documents.forEach { doc ->
                        userById[doc.id] = doc.toObject(User::class.java)
                    }
            } catch (_: Exception) {
            }
        }
        return members.map { m ->
            val uid = m.userId
            val u = userById[uid] ?: userRepository.getUser(uid)
            val displayName = u?.name?.trim()?.takeIf { it.isNotEmpty() } ?: m.name
            val displayEmail = u?.email?.trim()?.takeIf { it.isNotEmpty() } ?: m.email
            // users/{uid} is source of truth for profile media; blank there means no photo (ignore stale member doc).
            val mergedPhoto = when {
                u != null && !u.photoUrl.isNullOrBlank() -> u.photoUrl
                u != null -> null
                !m.photoUrl.isNullOrBlank() -> m.photoUrl
                else -> null
            }
            val noPhoto = mergedPhoto.isNullOrBlank()
            val (let, bg) = if (noPhoto && u != null) {
                userRepository.ensureAvatarIdentityFields(uid)
            } else if (noPhoto) {
                Pair(
                    com.manikandan.tripoo.utils.UserAvatarIdentity.letterFromName(displayName),
                    com.manikandan.tripoo.utils.UserAvatarIdentity.bgForSeed(uid)
                )
            } else {
                Pair(
                    u?.avatarLetter?.ifBlank { null }
                        ?: com.manikandan.tripoo.utils.UserAvatarIdentity.letterFromName(displayName),
                    u?.avatarColorHex?.ifBlank { null }
                        ?: com.manikandan.tripoo.utils.UserAvatarIdentity.bgForSeed(uid)
                )
            }
            m.copy(
                name = displayName,
                email = displayEmail,
                photoUrl = mergedPhoto,
                avatarLetter = let,
                avatarColorHex = bg
            )
        }
    }

    /**
     * Pushes [User] profile fields into every [TripMember] doc for this user so trip listeners refresh
     * and other devices see updated name/photo. Uses merge so [TripMember.isAdmin] is preserved.
     */
    suspend fun syncMemberProfileFromUser(uid: String) {
        if (uid.isBlank()) return
        val user = userRepository.getUser(uid) ?: return
        val tripIds = user.tripIds.distinct().filter { it.isNotBlank() }
        if (tripIds.isEmpty()) return

        val name = user.name.trim()
        val email = user.email.trim()
        val photoUrl = user.photoUrl?.trim().orEmpty()
        var letter = user.avatarLetter?.trim().orEmpty()
        if (letter.isEmpty()) {
            letter = com.manikandan.tripoo.utils.UserAvatarIdentity.letterFromName(
                name.ifBlank { user.email.substringBefore("@") }
            )
        }
        var colorHex = user.avatarColorHex?.trim().orEmpty()
        if (colorHex.isEmpty()) {
            colorHex = com.manikandan.tripoo.utils.UserAvatarIdentity.bgForSeed(uid)
        }

        val payload = mapOf(
            "name" to name,
            "email" to email,
            "photoUrl" to photoUrl,
            "avatarLetter" to letter,
            "avatarColorHex" to colorHex
        )

        tripIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            for (tripId in chunk) {
                batch.set(
                    trips.document(tripId).collection("members").document(uid),
                    payload,
                    SetOptions.merge()
                )
            }
            batch.commit().await()
        }
    }

    fun listenToTripMembers(tripId: String): Flow<List<TripMember>> = callbackFlow {
        var enrichJob: Job? = null
        val flowScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val listener = trips.document(tripId).collection("members")
            .addSnapshotListener { snap, e ->
                enrichJob?.cancel()
                if (e != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc -> parseTripMember(doc) } ?: emptyList()
                enrichJob = flowScope.launch {
                    val enriched = try {
                        withContext(Dispatchers.IO) { enrichMembersWithUserProfiles(list) }
                    } catch (_: Exception) {
                        list
                    }
                    trySend(enriched)
                }
            }
        awaitClose {
            enrichJob?.cancel()
            flowScope.cancel()
            listener.remove()
        }
    }

    fun listenToTripMembers(tripId: String, callback: (List<TripMember>, Exception?) -> Unit): ListenerRegistration {
        return trips.document(tripId).collection("members")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    memberEnrichJob?.cancel()
                    callback(emptyList(), e)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc -> parseTripMember(doc) } ?: emptyList()
                memberEnrichJob?.cancel()
                memberEnrichJob = memberEnrichScope.launch {
                    val enriched = try {
                        withContext(Dispatchers.IO) { enrichMembersWithUserProfiles(list) }
                    } catch (_: Exception) {
                        list
                    }
                    callback(enriched, null)
                }
            }
    }

    suspend fun setMemberAdminRole(
        tripId: String,
        targetUserId: String,
        asAdmin: Boolean,
        actingUserId: String,
        tripCreatorId: String
    ) {
        if (actingUserId != tripCreatorId) {
            throw SecurityException("Only the trip organiser can change member roles")
        }
        if (targetUserId == tripCreatorId) {
            throw SecurityException("The organiser cannot be demoted")
        }
        trips.document(tripId).collection("members").document(targetUserId)
            .update(
                mapOf(
                    "isAdmin" to asAdmin,
                    "admin" to asAdmin
                )
            ).await()
    }

    suspend fun removeMemberFromTrip(
        tripId: String,
        targetUserId: String,
        actingOrganiserId: String,
        tripOrganiserId: String
    ) {
        if (actingOrganiserId != tripOrganiserId) {
            throw SecurityException("Only the trip organiser can remove members")
        }
        if (targetUserId == tripOrganiserId) {
            throw SecurityException("Cannot remove the organiser")
        }
        val tripName = trips.document(tripId).get().await().toObject(Trip::class.java)?.name?.trim()
            .orEmpty().ifBlank { "Trip" }
        val removedSnap = trips.document(tripId).collection("members").document(targetUserId).get().await()
        val removedName = parseTripMember(removedSnap)?.name?.trim().orEmpty().ifBlank { "Someone" }
        FanoutNotificationPublisher.publishAsync(
            tripId,
            tripName,
            "$removedName was removed from the trip",
            FanoutTypes.MEMBER_REMOVED
        )
        val batch = db.batch()
        batch.delete(trips.document(tripId).collection("members").document(targetUserId))
        batch.update(trips.document(tripId), "memberIds", FieldValue.arrayRemove(targetUserId))
        batch.commit().await()
        userRepository.removeTripFromUser(targetUserId, tripId)
    }

    fun removeMemberFromTrip(
        tripId: String,
        targetUserId: String,
        actingOrganiserId: String,
        tripOrganiserId: String,
        callback: (Throwable?) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    removeMemberFromTrip(tripId, targetUserId, actingOrganiserId, tripOrganiserId)
                }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    fun setMemberAdminRole(
        tripId: String,
        targetUserId: String,
        asAdmin: Boolean,
        actingUserId: String,
        tripCreatorId: String,
        callback: (Throwable?) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    setMemberAdminRole(tripId, targetUserId, asAdmin, actingUserId, tripCreatorId)
                }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    fun leaveTripAsync(tripId: String, uid: String, callback: (LeaveTripResult) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) { leaveTrip(tripId, uid) }
            callback(result)
        }
    }

    fun transferAdminAndLeave(tripId: String, oldUid: String, newUid: String, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { transferAdminAndLeave(tripId, oldUid, newUid) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    suspend fun leaveTrip(tripId: String, uid: String): LeaveTripResult {
        return try {
            val members = getTripMembers(tripId)
            if (members.size == 1) return LeaveTripResult.LastMember
            val me = members.find { it.userId == uid } ?: return LeaveTripResult.Success
            if (me.isAdmin) {
                val otherAdmins = members.filter { it.isAdmin && it.userId != uid }
                if (otherAdmins.isEmpty()) {
                    return LeaveTripResult.MustTransferAdmin(members.filter { it.userId != uid })
                }
            }
            val tripName = trips.document(tripId).get().await().toObject(Trip::class.java)?.name?.trim()
                .orEmpty().ifBlank { "Trip" }
            FanoutNotificationPublisher.publishAsync(
                tripId,
                tripName,
                "${me.name} left the trip",
                FanoutTypes.MEMBER_LEFT
            )
            val batch = db.batch()
            batch.delete(trips.document(tripId).collection("members").document(uid))
            batch.update(trips.document(tripId), "memberIds", FieldValue.arrayRemove(uid))
            batch.commit().await()
            LeaveTripResult.Success
        } catch (e: Exception) {
            LeaveTripResult.Success
        }
    }

    /**
     * Removes [uid] from every trip in their [User.tripIds] snapshot (leave, transfer admin, or delete trip
     * if they are the only member). Caller should delete the user document and Firebase Auth account.
     */
    suspend fun removeAccountFromAllTrips(uid: String) {
        val initial = userRepository.getUser(uid)?.tripIds?.toList().orEmpty()
        for (tripId in initial) {
            if (tripId.isBlank()) continue
            try {
                val members = getTripMembers(tripId)
                when {
                    members.isEmpty() -> { /* stale id */ }
                    members.size == 1 && members.first().userId == uid ->
                        deleteTripAsAdmin(tripId, uid)
                    else -> {
                        when (val r = leaveTrip(tripId, uid)) {
                            is LeaveTripResult.MustTransferAdmin -> {
                                val next = r.otherMembers.firstOrNull()
                                if (next != null) {
                                    transferAdminAndLeave(tripId, uid, next.userId)
                                }
                            }
                            is LeaveTripResult.LastMember ->
                                deleteTripAsAdmin(tripId, uid)
                            is LeaveTripResult.Success -> { }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            try {
                userRepository.removeTripFromUser(uid, tripId)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun transferAdminAndLeave(tripId: String, oldUid: String, newUid: String) {
        try {
            val tripName = trips.document(tripId).get().await().toObject(Trip::class.java)?.name?.trim()
                .orEmpty().ifBlank { "Trip" }
            FanoutNotificationPublisher.publishAsync(
                tripId,
                tripName,
                "Trip admin was transferred",
                FanoutTypes.ADMIN_TRANSFER
            )
            val batch = db.batch()
            val membersRef = trips.document(tripId).collection("members")
            batch.update(
                membersRef.document(newUid),
                mapOf("isAdmin" to true, "admin" to true)
            )
            batch.update(trips.document(tripId), "adminId", newUid)
            batch.delete(membersRef.document(oldUid))
            batch.update(trips.document(tripId), "memberIds", FieldValue.arrayRemove(oldUid))
            batch.commit().await()
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun deleteTripAsAdmin(tripId: String, adminUid: String) {
        try {
            val tripRef = trips.document(tripId)
            val tripName = tripRef.get().await().toObject(Trip::class.java)?.name?.trim()
                .orEmpty().ifBlank { "Trip" }
            FanoutNotificationPublisher.publishAsync(
                tripId,
                tripName,
                "This trip was deleted",
                FanoutTypes.TRIP_DELETED
            )

            // 1) Delete expenses + tasks (batched)
            suspend fun deleteSubcollection(sub: String) {
                val docs = tripRef.collection(sub).get().await().documents
                docs.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }

            deleteSubcollection("expenses")
            deleteSubcollection("tasks")
            deleteSubcollection("settlements")
            deleteSubcollection("itinerary")
            deleteSubcollection("polls")

            // 2) Delete members except admin (keep admin member doc so isAdmin() continues to pass)
            val memberDocs = tripRef.collection("members").get().await().documents
            val others = memberDocs.filter { it.id != adminUid }
            others.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            // 3) Delete trip doc (requires admin member doc still exists)
            tripRef.delete().await()

            // 4) Delete admin member doc last (optional cleanup)
            tripRef.collection("members").document(adminUid).delete().await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun deleteTripAsAdmin(tripId: String, adminUid: String, callback: (Throwable?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { deleteTripAsAdmin(tripId, adminUid) }
                callback(null)
            } catch (e: Exception) {
                callback(e)
            }
        }
    }

    fun listenToTrip(tripId: String, listener: (Trip?, Exception?) -> Unit): ListenerRegistration {
        return trips.document(tripId).addSnapshotListener { snapshot, e ->
            if (e != null) {
                listener(null, e)
                return@addSnapshotListener
            }
            val trip = snapshot?.toObject(Trip::class.java)?.copy(id = snapshot.id)
            listener(trip, null)
        }
    }

    fun createTrip(trip: Trip, adminMember: TripMember, callback: (String?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val tripId = withContext(Dispatchers.IO) { createTrip(trip, adminMember) }
            callback(tripId)
        }
    }

    fun joinTrip(joinCode: String, member: TripMember, callback: (String?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val tripId = withContext(Dispatchers.IO) { joinTrip(joinCode, member) }
            callback(tripId)
        }
    }
}
