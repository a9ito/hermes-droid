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

    // -- filteredBy ---------------------------------------------------------------

    private fun full(id: String, title: String, model: String? = null, preview: String? = null) =
        SessionSummary(id = id, title = title, model = model, preview = preview)

    @Test fun blankQueryReturnsEverything() {
        val input = listOf(full("1", "alpha"), full("2", "beta"))
        assertEquals(input, input.filteredBy(""))
        assertEquals(input, input.filteredBy("   "))
    }

    @Test fun filterMatchesTitleModelAndPreviewCaseInsensitively() {
        val input = listOf(
            full("1", "Kernel debugging", model = "claude-opus", preview = "stack trace"),
            full("2", "Groceries", model = "gpt-5", preview = "milk and eggs"),
            full("3", "Random", model = "deepseek", preview = "OPUS was here"),
        )
        // Title match (case-insensitive)
        assertEquals(listOf("1"), input.filteredBy("KERNEL").map { it.id })
        // Model match on one, preview match on another — both surface
        assertEquals(listOf("1", "3"), input.filteredBy("opus").map { it.id })
        // Preview-only match
        assertEquals(listOf("2"), input.filteredBy("eggs").map { it.id })
    }

    @Test fun filterPreservesInputOrderAndDropsNonMatches() {
        val input = listOf(full("1", "aaa"), full("2", "bbb"), full("3", "aab"))
        assertEquals(listOf("1", "3"), input.filteredBy("aa").map { it.id })
    }

    @Test fun filterWithNoMatchIsEmpty() {
        val input = listOf(full("1", "alpha"), full("2", "beta"))
        assertEquals(emptyList<String>(), input.filteredBy("zzz").map { it.id })
    }
}
