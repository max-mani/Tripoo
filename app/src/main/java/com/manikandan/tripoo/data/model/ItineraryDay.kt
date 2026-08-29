package com.manikandan.tripoo.data.model

data class ItineraryStop(
    val id: String = "",
    val time: String = "",
    val title: String = "",
    val location: String = "",
    val notes: String = "",
    val createdBy: String = ""
)

data class ItineraryDay(
    val id: String = "",
    val dayIndex: Int = 0,
    /** Epoch millis at local start of day; 0 means undated. */
    val date: Long = 0L,
    val stops: List<ItineraryStop> = emptyList()
)
