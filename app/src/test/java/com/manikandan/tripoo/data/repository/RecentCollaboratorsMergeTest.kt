package com.manikandan.tripoo.data.repository

import com.manikandan.tripoo.data.model.RecentCollaborator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentCollaboratorsMergeTest {
    @Test
    fun dedupesByUidAndCapsAt20DroppingOldest() {
        val existing = (1..20).map { i ->
            RecentCollaborator(uid = "u$i", name = "N$i", lastSeenAt = i.toLong())
        }
        val incoming = listOf(RecentCollaborator(uid = "new", name = "New"))
        val merged = UserRepository.mergeCollaboratorLists(existing, incoming, now = 1000L, cap = 20)
        assertEquals(20, merged.size)
        assertTrue(merged.any { it.uid == "new" })
        assertFalse(merged.any { it.uid == "u1" })
        assertEquals(1000L, merged.first { it.uid == "new" }.lastSeenAt)
    }

    @Test
    fun parseListOfMaps() {
        val raw = listOf(
            mapOf("uid" to "a", "name" to "Alex", "lastSeenAt" to 5L),
            mapOf("uid" to "", "name" to "skip")
        )
        val parsed = UserRepository.parseRecentCollaborators(raw)
        assertEquals(1, parsed.size)
        assertEquals("Alex", parsed[0].name)
    }

    @Test
    fun skipWriteWhenRecentlySeen() {
        val existing = listOf(RecentCollaborator("a", "Alex", null, 900L))
        val incoming = listOf(RecentCollaborator("a", "Alex"))
        assertTrue(UserRepository.shouldSkipRecentWrite(existing, incoming, now = 1000L))
        assertFalse(UserRepository.shouldSkipRecentWrite(existing, incoming, now = 900L + UserRepository.SKIP_IF_SEEN_WITHIN_MS + 1))
    }
}
