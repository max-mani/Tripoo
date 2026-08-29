package com.manikandan.tripoo.data.model

object OutingCategories {
    data class Item(val label: String, val emoji: String)

    val ALL: List<Item> = listOf(
        Item("Food", "\uD83C\uDF7D"),
        Item("Movie", "\uD83C\uDFAC"),
        Item("Party", "\uD83C\uDF89"),
        Item("Shopping", "\uD83D\uDED2"),
        Item("Other", "\uD83D\uDCE6")
    )

    @JvmStatic
    fun emojiForDescription(description: String?): String {
        val d = description?.trim().orEmpty()
        if (d.isEmpty()) return ""
        return ALL.firstOrNull { it.label.equals(d, ignoreCase = true) }?.emoji.orEmpty()
    }
}
