package com.manikandan.tripoo.data.repository

import com.manikandan.tripoo.data.model.LeaveTripResult
import com.manikandan.tripoo.data.model.Trip
import com.manikandan.tripoo.data.model.TripMember
import com.manikandan.tripoo.notifications.FanoutNotificationPublisher
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
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

class TripRepository {
    private val db = FirebaseFirestore.getInstance()
    private val trips = db.collection("trips")

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
                "member_joined"
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
            "trip_edited"
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
                .documents.mapNotNull { doc -> doc.toObject(TripMember::class.java)?.copy(userId = doc.id) }
            if (memberDocs.isEmpty())
                return emptyList()

            // Member documents are written at join time and don't track profile photo changes.
            // Enrich each member with the freshest photoUrl from the users collection.
            val userIds = memberDocs.map { it.userId }.filter { it.isNotEmpty() }
            val photoMap: Map<String, String> = userIds.chunked(10).flatMap { chunk ->
                try {
                    db.collection("users")
                        .whereIn(FieldPath.documentId(), chunk)
                        .get().await()
                        .documents
                        .mapNotNull { doc ->
                            val photo = doc.getString("photoUrl")
                            if (!photo.isNullOrEmpty()) doc.id to photo else null
                        }
                } catch (e: Exception) { emptyList() }
            }.toMap()

            memberDocs.map { member ->
                photoMap[member.userId]?.let { member.copy(photoUrl = it) } ?: member
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun listenToTripMembers(tripId: String): Flow<List<TripMember>> = callbackFlow {
        val listener = trips.document(tripId).collection("members")
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { doc ->
                    doc.toObject(TripMember::class.java)?.copy(userId = doc.id)
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    fun listenToTripMembers(tripId: String, callback: (List<TripMember>, Exception?) -> Unit): ListenerRegistration {
        return trips.document(tripId).collection("members")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    callback(emptyList(), e)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(TripMember::class.java)?.copy(userId = doc.id)
                } ?: emptyList()
                callback(list, null)
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
                "member_left"
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

    suspend fun transferAdminAndLeave(tripId: String, oldUid: String, newUid: String) {
        try {
            val tripName = trips.document(tripId).get().await().toObject(Trip::class.java)?.name?.trim()
                .orEmpty().ifBlank { "Trip" }
            FanoutNotificationPublisher.publishAsync(
                tripId,
                tripName,
                "Trip admin was transferred",
                "admin_transfer"
            )
            val batch = db.batch()
            val membersRef = trips.document(tripId).collection("members")
            batch.update(membersRef.document(newUid), "isAdmin", true)
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
                "trip_deleted"
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
