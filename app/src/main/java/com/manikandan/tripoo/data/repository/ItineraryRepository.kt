package com.manikandan.tripoo.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.manikandan.tripoo.data.model.ItineraryDay
import com.manikandan.tripoo.data.model.ItineraryStop
import com.manikandan.tripoo.notifications.FanoutNotificationPublisher
import com.manikandan.tripoo.notifications.FanoutTypes
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

class ItineraryRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun col(tripId: String) =
        db.collection("trips").document(tripId).collection("itinerary")

    fun listenToDays(tripId: String, callback: (List<ItineraryDay>, Exception?) -> Unit): ListenerRegistration {
        return col(tripId).orderBy("dayIndex", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    callback(emptyList(), e)
                    return@addSnapshotListener
                }
                val days = snap?.documents?.map { parseDay(it.id, it.data.orEmpty()) }.orEmpty()
                callback(days, null)
            }
    }

    suspend fun ensureDays(tripId: String, startDate: Long, endDate: Long, outing: Boolean) {
        val existing = col(tripId).limit(1).get().await()
        if (!existing.isEmpty) return
        if (outing || startDate <= 0L || endDate <= 0L) {
            col(tripId).document().set(
                mapOf(
                    "dayIndex" to 0,
                    "date" to 0L,
                    "stops" to emptyList<Map<String, Any>>()
                )
            ).await()
            return
        }
        val days = localDaysInclusive(startDate, endDate)
        days.forEachIndexed { index, dateStart ->
            col(tripId).document().set(
                mapOf(
                    "dayIndex" to index,
                    "date" to dateStart,
                    "stops" to emptyList<Map<String, Any>>()
                )
            ).await()
        }
    }

    suspend fun addStop(tripId: String, dayId: String, stop: ItineraryStop) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        rmwStops(tripId, dayId) { list ->
            list + stop.copy(
                id = stop.id.ifBlank { UUID.randomUUID().toString() },
                createdBy = uid.ifBlank { stop.createdBy }
            )
        }
        FanoutNotificationPublisher.publishAsync(
            tripId, "Itinerary", "Itinerary updated: ${stop.title}", FanoutTypes.ITINERARY_UPDATED
        )
    }

    suspend fun updateStop(tripId: String, dayId: String, stop: ItineraryStop) {
        rmwStops(tripId, dayId) { list ->
            list.map { if (it.id == stop.id) stop else it }
        }
        FanoutNotificationPublisher.publishAsync(
            tripId, "Itinerary", "Itinerary updated: ${stop.title}", FanoutTypes.ITINERARY_UPDATED
        )
    }

    suspend fun deleteStop(tripId: String, dayId: String, stopId: String) {
        rmwStops(tripId, dayId) { list -> list.filter { it.id != stopId } }
        FanoutNotificationPublisher.publishAsync(
            tripId, "Itinerary", "A stop was removed", FanoutTypes.ITINERARY_UPDATED
        )
    }

    private suspend fun rmwStops(tripId: String, dayId: String, transform: (List<ItineraryStop>) -> List<ItineraryStop>) {
        val ref = col(tripId).document(dayId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val day = parseDay(dayId, snap.data.orEmpty())
            val next = transform(day.stops)
            tx.update(ref, "stops", next.map { stopToMap(it) })
        }.await()
    }

    private fun stopToMap(s: ItineraryStop): Map<String, Any> = mapOf(
        "id" to s.id,
        "time" to s.time,
        "title" to s.title,
        "location" to s.location,
        "notes" to s.notes,
        "createdBy" to s.createdBy
    )

    companion object {
        @JvmStatic
        fun parseDay(id: String, data: Map<String, Any>): ItineraryDay {
            val stopsRaw = data["stops"] as? List<*> ?: emptyList<Any>()
            val stops = stopsRaw.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                ItineraryStop(
                    id = m["id"] as? String ?: "",
                    time = m["time"] as? String ?: "",
                    title = m["title"] as? String ?: "",
                    location = m["location"] as? String ?: "",
                    notes = m["notes"] as? String ?: "",
                    createdBy = m["createdBy"] as? String ?: ""
                )
            }
            val date = when (val d = data["date"]) {
                is Number -> d.toLong()
                else -> 0L
            }
            val index = when (val i = data["dayIndex"]) {
                is Number -> i.toInt()
                else -> 0
            }
            return ItineraryDay(id = id, dayIndex = index, date = date, stops = stops)
        }

        @JvmStatic
        fun startOfLocalDay(epochMillis: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = epochMillis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        @JvmStatic
        fun localDaysInclusive(startDate: Long, endDate: Long): List<Long> {
            val start = startOfLocalDay(startDate)
            val end = startOfLocalDay(endDate)
            if (end < start) return listOf(start)
            val out = mutableListOf<Long>()
            var t = start
            val dayMs = 24L * 60 * 60 * 1000
            while (t <= end) {
                out.add(t)
                t += dayMs
            }
            return out
        }
    }
}
