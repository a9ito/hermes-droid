package com.a9ito.hermesagent.data.remote

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bounded SSE line reader contract. Proves the DoS guard: a hostile server line
 * with no terminator cannot grow okio's buffer without limit — it is aborted
 * once it exceeds the cap, while well-formed lines read exactly as before.
 */
class SseLineReaderTest {

    private fun buf(s: String) = Buffer().apply { writeUtf8(s) }

    @Test fun readsLfLines() {
        val b = buf("data: hello\ndata: world\n")
        assertEquals("data: hello", SseLineReader.readLine(b))
        assertEquals("data: world", SseLineReader.readLine(b))
        assertNull(SseLineReader.readLine(b))
    }

    @Test fun stripsCrlf() {
        val b = buf("event: ping\r\ndata: {}\r\n")
        assertEquals("event: ping", SseLineReader.readLine(b))
        assertEquals("data: {}", SseLineReader.readLine(b))
        assertNull(SseLineReader.readLine(b))
    }

    @Test fun returnsTruncatedFinalLineWithoutNewlineThenNull() {
        val b = buf("data: partial")
        assertEquals("data: partial", SseLineReader.readLine(b))
        assertNull(SseLineReader.readLine(b))
    }

    @Test fun emptyLinesPreservedAsFrameBoundaries() {
        val b = buf("\n\n")
        assertEquals("", SseLineReader.readLine(b))
        assertEquals("", SseLineReader.readLine(b))
        assertNull(SseLineReader.readLine(b))
    }

    @Test fun lineExactlyAtLimitIsAccepted() {
        val limit = 8L
        val b = buf("abcdefgh\n") // 8 content bytes + newline
        assertEquals("abcdefgh", SseLineReader.readLine(b, limit))
    }

    @Test fun unterminatedLineOverLimitThrows() {
        val limit = 16L
        // 100 bytes, no newline anywhere: the abusive case.
        val b = buf("x".repeat(100))
        assertThrows(SseLineTooLongException::class.java) {
            SseLineReader.readLine(b, limit)
        }
    }

    @Test fun earlyNewlineWithinLimitIsNotAffectedByOverlongTail() {
        val limit = 8L
        // A short line, then a huge unterminated tail. The first read must still
        // return the short line (no false positive from the later bytes).
        val b = buf("ok\n" + "y".repeat(1000))
        assertEquals("ok", SseLineReader.readLine(b, limit))
        // The next read hits the overlong tail and aborts.
        assertThrows(SseLineTooLongException::class.java) {
            SseLineReader.readLine(b, limit)
        }
    }

    @Test fun defaultLimitIsSixteenMiB() {
        assertEquals(16L * 1024 * 1024, SseLineReader.MAX_LINE_BYTES)
        // A line comfortably under the default cap reads fine.
        val b = buf("data: " + "z".repeat(1_000_000) + "\n")
        val line = SseLineReader.readLine(b)
        assertTrue(line != null && line.length > 1_000_000)
    }
}
