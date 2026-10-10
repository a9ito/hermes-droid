package com.a9ito.hermesagent.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared JsonObject primitive extractors replaced the hand-rolled local
 * `str/strOrNull/bool` helpers that the two SSE parsers and the DTO mappers each
 * duplicated. These pin the contract both decoders rely on: a missing or
 * wrong-typed key degrades to the documented default rather than throwing.
 */
class JsonExtensionsTest {

    private fun obj(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    @Test fun stringOrEmptyReadsStringElseBlank() {
        val o = obj("""{"a":"hi","n":5,"b":true,"nul":null}""")
        assertEquals("hi", o.stringOrEmpty("a"))
        // A number primitive has string content too (kotlinx), so this reads "5".
        assertEquals("5", o.stringOrEmpty("n"))
        assertEquals("", o.stringOrEmpty("missing"))
        assertEquals("", o.stringOrEmpty("nul"))
    }

    @Test fun stringOrNullDropsEmptyAndMissing() {
        val o = obj("""{"a":"hi","empty":"","nul":null}""")
        assertEquals("hi", o.stringOrNull("a"))
        assertNull(o.stringOrNull("empty"))
        assertNull(o.stringOrNull("missing"))
        assertNull(o.stringOrNull("nul"))
    }

    @Test fun booleanOrFalseReadsBoolElseFalse() {
        val o = obj("""{"t":true,"f":false,"s":"true","word":"nope","n":1}""")
        assertTrue(o.booleanOrFalse("t"))
        assertFalse(o.booleanOrFalse("f"))
        // kotlinx booleanOrNull reads content.toBooleanStrictOrNull() regardless of
        // isString, so a quoted "true" still coerces to true — this matches the
        // pre-refactor local bool() helper exactly (the server sends a real JSON
        // bool for the only consumer, tool.completed "error").
        assertTrue(o.booleanOrFalse("s"))
        // A non-boolean word or a number is not strict-parseable -> false.
        assertFalse(o.booleanOrFalse("word"))
        assertFalse(o.booleanOrFalse("n"))
        assertFalse(o.booleanOrFalse("missing"))
    }

    @Test fun longOrZeroReadsNumberElseZero() {
        val o = obj("""{"n":1000,"big":296915703,"neg":-5,"s":"42","f":1.9,"nul":null}""")
        assertEquals(1000L, o.longOrZero("n"))
        assertEquals(296915703L, o.longOrZero("big"))
        assertEquals(-5L, o.longOrZero("neg"))
        // A quoted numeric string is still a primitive with longOrNull content.
        assertEquals(42L, o.longOrZero("s"))
        // A fractional value has no long form -> 0 (longOrNull is null).
        assertEquals(0L, o.longOrZero("f"))
        assertEquals(0L, o.longOrZero("missing"))
        assertEquals(0L, o.longOrZero("nul"))
    }
}
