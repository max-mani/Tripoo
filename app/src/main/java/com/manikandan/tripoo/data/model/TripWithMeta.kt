package com.manikandan.tripoo.data.model

data class TripWithMeta(
    val trip: Trip,
    val memberCount: Int,
    val userRole: String,  // "admin" or "member"
    val totalSpent: Double = 0.0
)

