package com.a9ito.hermesagent.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior contract for the session SSE parser: it must track the most recent
 * event: line and decode the following data: line against it, skip keep-alives
 * and boundaries, and recognize the terminal frames.
 */
class SessionSseParserTest {

    private fun feed(lines: List<String>): List<SessionStreamEvent> {
        val parser = SessionSseParser()
        return lines.mapNotNull { parser.onLine(it) }
    }

    @Test fun decodesDeltaAgainstPrecedingEventLine() {
        val events = feed(
            listOf(
                "event: assistant.delta",
                """data: {"message_id":"m1","delta":"Ha"}""",
                "",
                "event: assistant.delta",
                """data: {"message_id":"m1","delta":"lo"}""",
            )
        )
        assertEquals(
            listOf("Ha", "lo"),
            events.filterIsInstance<SessionStreamEvent.Delta>().map { it.text },
        )
    }

    @Test fun keepAliveAndBoundaryLinesYieldNothing() {
        assertNull(SessionSseParser().onLine(": keepalive"))
        assertNull(SessionSseParser().onLine(""))
    }

    @Test fun completedCarriesContent() {
        val events = feed(
            listOf(
                "event: assistant.completed",
                """data: {"session_id":"s1","content":"final text"}""",
            )
        )
        val completed = events.filterIsInstance<SessionStreamEvent.Completed>().single()
        assertEquals("final text", completed.content)
    }

    @Test fun errorFrameIsFailed() {
        val events = feed(
            listOf("event: error", """data: {"message":"boom"}""")
        )
        assertEquals("boom", events.filterIsInstance<SessionStreamEvent.Failed>().single().message)
    }

    @Test fun doneEventAndDoneSentinelBothClose() {
        assertTrue(feed(listOf("event: done", "data: {}")).last() is SessionStreamEvent.Done)
        assertTrue(feed(listOf("data: [DONE]")).last() is SessionStreamEvent.Done)
    }

    @Test fun unknownEventsAreIgnoredNotErrors() {
        val events = feed(
            listOf(
                "event: run.started",
                """data: {"user_message":{"role":"user","content":"hi"}}""",
                "event: message.started",
                """data: {"message":{"id":"m1"}}""",
            )
        )
        assertTrue(events.all { it is SessionStreamEvent.Ignored })
    }

    @Test fun toolProgressIsThinking() {
        val events = feed(listOf("event: tool.progress", """data: {"tool_name":"_thinking","delta":"hmm"}"""))
        assertTrue(events.single() is SessionStreamEvent.Thinking)
    }

    @Test fun toolLifecycleFramesDecode() {
        val events = feed(
            listOf(
                "event: tool.started",
                """data: {"tool_name":"terminal"}""",
                "event: tool.completed",
                """data: {"tool_name":"terminal"}""",
                "event: tool.failed",
                """data: {"tool_name":"web_search"}""",
            )
        )
        assertEquals("terminal", events.filterIsInstance<SessionStreamEvent.ToolStarted>().single().toolName)
        assertEquals("terminal", events.filterIsInstance<SessionStreamEvent.ToolCompleted>().single().toolName)
        assertEquals("web_search", events.filterIsInstance<SessionStreamEvent.ToolFailed>().single().toolName)
    }

    @Test fun commentaryCarriesTextAndEmptyIsIgnored() {
        val withText = feed(listOf("event: assistant.commentary", """data: {"text":"let me check"}"""))
        assertEquals("let me check", withText.filterIsInstance<SessionStreamEvent.Commentary>().single().text)
        val empty = feed(listOf("event: assistant.commentary", """data: {"text":""}"""))
        assertTrue(empty.single() is SessionStreamEvent.Ignored)
    }

    @Test fun deltaWithoutContentKeyIsEmptyNotCrash() {
        val events = feed(listOf("event: assistant.delta", "data: {}"))
        assertEquals("", events.filterIsInstance<SessionStreamEvent.Delta>().single().text)
    }

    @Test fun malformedJsonDoesNotThrow() {
        // A truncated data line must degrade to Ignored, never crash the stream.
        val events = feed(listOf("event: assistant.delta", "data: {not json"))
        assertTrue(events.single() is SessionStreamEvent.Ignored)
    }

    @Test fun terminalRunEventCarriesUsageTokens() {
        val events = feed(
            listOf(
                "event: run.completed",
                """data: {"message_id":"m1","usage":{"input_tokens":900,"output_tokens":100,"total_tokens":1000}}""",
            )
        )
        assertEquals(1000L, events.filterIsInstance<SessionStreamEvent.Usage>().single().totalTokens)
    }

    @Test fun terminalRunEventWithoutUsageIsIgnoredNotUsage() {
        // run.started and a run.<status> with no usage block must not emit a phantom Usage(0).
        val events = feed(
            listOf(
                "event: run.started",
                """data: {"user_message":{"role":"user","content":"hi"}}""",
                "event: run.completed",
                """data: {"message_id":"m1"}""",
            )
        )
        assertTrue(events.all { it is SessionStreamEvent.Ignored })
    }
}
