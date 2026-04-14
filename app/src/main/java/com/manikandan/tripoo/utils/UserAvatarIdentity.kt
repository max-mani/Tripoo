package com.manikandan.tripoo.utils

import android.graphics.Color
import com.manikandan.tripoo.data.model.TripMember
import kotlin.math.abs

object UserAvatarIdentity {
    /** Background colors (no photo) — stable palette from design */
    val AVATAR_BG_HEX: List<String> = listOf(
        "#FFEDD5", // warm peach text #C05C00
        "#DCFCE7", // green
        "#DBEAFE", // blue
        "#F3E8FF", // purple
        "#FEF9C3", // yellow
        "#FFE4E6", // rose
        "#E0F2FE", // sky
        "#F5F5F4"  // stone
    )

    val AVATAR_TEXT_HEX: List<String> = listOf(
        "#C05C00",
        "#16A34A",
        "#2563EB",
        "#9333EA",
        "#CA8A04",
        "#BE123C",
        "#0369A1",
        "#57534D"
    )

    fun letterFromName(name: String?): String {
        val t = name?.trim().orEmpty()
        if (t.isEmpty()) return "?"
        val c = t.first().uppercaseChar()
        return if (c.isLetterOrDigit()) c.toString() else "?"
    }

    fun pickPaletteIndex(seed: String): Int {
        if (seed.isEmpty()) return 0
        return abs(seed.hashCode()) % AVATAR_BG_HEX.size
    }

    fun bgForSeed(seed: String): String = AVATAR_BG_HEX[pickPaletteIndex(seed)]

    fun textColorForSeed(seed: String): String = AVATAR_TEXT_HEX[pickPaletteIndex(seed)]

    /** Text colour that pairs with a persisted background hex, or falls back to [seed]. */
    fun textColorForBackgroundHex(bgHex: String?, seed: String): String {
        val h = bgHex?.trim()?.takeIf { it.isNotEmpty() } ?: return textColorForSeed(seed)
        val idx = AVATAR_BG_HEX.indexOf(h)
        return if (idx >= 0) AVATAR_TEXT_HEX[idx % AVATAR_TEXT_HEX.size] else textColorForSeed(seed)
    }

    /** Single letter for chips / badges, preferring persisted [TripMember.avatarLetter]. */
    fun displayLetter(member: TripMember): Char {
        val t = member.avatarLetter?.trim().orEmpty()
        if (t.isNotEmpty()) return t[0].uppercaseChar()
        return letterFromName(member.name).firstOrNull()?.uppercaseChar() ?: '?'
    }

    /** Background + text colours for UI chips; uses DB hex when set, else deterministic palette. */
    fun chipColors(member: TripMember, listIndex: Int): Pair<Int, Int> {
        val hex = member.avatarColorHex?.trim()?.takeIf { it.isNotEmpty() }
        val bgParsed = hex?.let { runCatching { Color.parseColor(it) }.getOrNull() }
        if (bgParsed != null) {
            val idx = AVATAR_BG_HEX.indexOf(hex).takeIf { it >= 0 } ?: pickPaletteIndex(member.userId)
            val txt = Color.parseColor(AVATAR_TEXT_HEX[idx % AVATAR_TEXT_HEX.size])
            return bgParsed to txt
        }
        val i = pickPaletteIndex("${member.userId}_$listIndex")
        return Color.parseColor(AVATAR_BG_HEX[i]) to Color.parseColor(AVATAR_TEXT_HEX[i])
    }
}
