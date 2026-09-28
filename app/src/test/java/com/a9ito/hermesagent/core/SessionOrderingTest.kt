package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Display-ordering contract for the sessions list: pins float up, server order is
 * preserved within each group (stable), and nothing is dropped.
 */
class SessionOrderingTest {

    private fun s(id: String, pinned: Boolean = false) = SessionSummary(id = id, title = id, pinned = pinned)

    @Test fun pinnedFloatToTopPreservingServerOrder() {
        val input = listOf(s("a"), s("b", pinned = true), s("c"), s("d", pinned = true))
        val out = input.sortedForDisplay().map { it.id }
        // Pinned b,d keep their relative server order; then unpinned a,c keep theirs.
        assertEquals(listOf("b", "d", "a", "c"), out)
    }

    @Test fun allUnpinnedKeepsServerOrder() {
        val input = listOf(s("a"), s("b"), s("c"))
        assertEquals(listOf("a", "b", "c"), input.sortedForDisplay().map { it.id })
    }

    @Test fun allPinnedKeepsServerOrder() {
        val input = listOf(s("a", pinned = true), s("b", pinned = true))
        assertEquals(listOf("a", "b"), input.sortedForDisplay().map { it.id })
    }

    @Test fun emptyStaysEmpty() {
        assertEquals(emptyList<String>(), emptyList<SessionSummary>().sortedForDisplay().map { it.id })
    }

    @Test fun noSessionIsDropped() {
        val input = (1..10).map { s("s$it", pinned = it % 3 == 0) }
        assertEquals(input.map { it.id }.toSet(), input.sortedForDisplay().map { it.id }.toSet())
    }
}
