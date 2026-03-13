package com.example.tripoo.data.model

data class Expense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "other",
    val paidBy: String = "",
    val splitWith: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
