package com.manikandan.tripoo.data.model

data class Settlement(
    val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val amount: Double = 0.0,
    val note: String? = null,
    val createdBy: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
