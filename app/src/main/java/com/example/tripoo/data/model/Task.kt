package com.example.tripoo.data.model

data class Task(
    val id: String = "",
    val title: String = "",
    val category: String = "general",
    val assignedTo: String = "everyone",
    val completed: Boolean = false,
    val dueDate: Long? = null,
    val priority: String = "medium",
    val notes: String? = null
) {
    companion object {
        const val CATEGORY_BOOKING = "booking"
        const val CATEGORY_PACKING = "packing"
        const val CATEGORY_GENERAL = "general"
    }
}
