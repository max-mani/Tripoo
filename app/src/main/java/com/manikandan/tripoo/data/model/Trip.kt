package com.manikandan.tripoo.data.model

data class Trip(
    val id: String = "",
    val name: String = "",
    val destination: String = "",
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val budget: Double = 0.0,
    val adminId: String = "",
    val joinCode: String = "",
    val memberIds: List<String> = emptyList(),
    val status: String = "upcoming"
)
