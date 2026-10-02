package com.a9ito.hermesagent.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningControlTest {

    private val json = Json

    // -- defaults: send nothing -----------------------------------------------

    @Test fun defaultPrefIsDefaultAndEmitsNoModelOptions() {
        val p = ReasoningPref.DEFAULT
        assertTrue(p.isDefault)
        assertNull(p.toModelOptions())
    }

    @Test fun explicitDefaultEffortNoFastStaysNull() {
        val p = ReasoningPref(effort = ReasoningEffort.DEFAULT, fast = false)
        assertNull(p.toModelOptions())
    }

    // -- effort shapes ---------------------------------------------------------

    @Test fun offEmitsReasoningDisabled() {
        val p = ReasoningPref(effort = ReasoningEffort.OFF)
        assertFalse(p.isDefault)
        val obj = p.toModelOptions()!!
        assertEquals("""{"reasoning":{"enabled":false}}""", json.encodeToString(obj))
    }

    @Test fun gradedEffortEmitsEnabledWithEffortString() {
        val obj = ReasoningPref(effort = ReasoningEffort.HIGH).toModelOptions()!!
        assertEquals("""{"reasoning":{"enabled":true,"effort":"high"}}""", json.encodeToString(obj))
    }

    @Test fun allGradedEffortsCarryTheirServerWireValue() {
        val graded = ReasoningEffort.entries.filter { it.wire != null }
        // minimal/low/medium/high/xhigh/max/ultra — exactly the server ladder minus none.
        assertEquals(listOf("minimal", "low", "medium", "high", "xhigh", "max", "ultra"), graded.map { it.wire })
    }

    // -- fast mode -------------------------------------------------------------

    @Test fun fastAloneEmitsFastTrueAndNoReasoning() {
        val obj = ReasoningPref(effort = ReasoningEffort.DEFAULT, fast = true).toModelOptions()!!
        assertEquals("""{"fast":true}""", json.encodeToString(obj))
    }

    @Test fun effortAndFastCombine() {
        val obj = ReasoningPref(effort = ReasoningEffort.LOW, fast = true).toModelOptions()!!
        assertEquals("""{"reasoning":{"enabled":true,"effort":"low"},"fast":true}""", json.encodeToString(obj))
    }

    @Test fun offAndFastCombine() {
        val obj = ReasoningPref(effort = ReasoningEffort.OFF, fast = true).toModelOptions()!!
        assertEquals("""{"reasoning":{"enabled":false},"fast":true}""", json.encodeToString(obj))
    }

    // -- key round-trip (local picker persistence contract) --------------------

    @Test fun effortFromKeyRoundTripsAndFallsBack() {
        ReasoningEffort.entries.forEach { assertEquals(it, ReasoningEffort.fromKey(it.key)) }
        assertEquals(ReasoningEffort.DEFAULT, ReasoningEffort.fromKey(null))
        assertEquals(ReasoningEffort.DEFAULT, ReasoningEffort.fromKey("bogus"))
    }
}
