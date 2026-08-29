package com.manikandan.tripoo.data.model

data class Poll(
    val id: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val votes: Map<String, Int> = emptyMap(),
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val closed: Boolean = false
)
