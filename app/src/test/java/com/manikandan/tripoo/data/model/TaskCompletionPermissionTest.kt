package com.manikandan.tripoo.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCompletionPermissionTest {
    @Test
    fun assigneeCanToggleButEveryoneCannot() {
        val mine = Task(assignedTo = "u1")
        assertTrue(mine.canToggleCompletion("u1", isLeader = false))
        assertFalse(mine.canToggleCompletion("u2", isLeader = false))
        assertTrue(mine.canToggleCompletion("u2", isLeader = true))

        val everyone = Task(assignedTo = "everyone")
        assertFalse(everyone.canToggleCompletion("u1", isLeader = false))
        assertTrue(everyone.canToggleCompletion("u1", isLeader = true))
    }
}
