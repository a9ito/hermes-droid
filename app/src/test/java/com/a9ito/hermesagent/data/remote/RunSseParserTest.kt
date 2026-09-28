package com.a9ito.hermesagent.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior contract for the /v1/runs/{id}/events SSE parser. Unlike the session
 * stream, the event name rides INSIDE the JSON ("event" field), so these tests
 * pin that: named events map correctly, run.<status> is terminal only for the
 * settled statuses, approval choices are preserved, and junk degrades to Ignored
 * (never a crash or phantom delta).
 */
class RunSseParserTest {

    private fun parser() = RunSseParser(Json { ignoreUnknownKeys = true })

    private fun feed(vararg lines: String): List<RunStreamEvent> {
        val p = parser()
        return lines.mapNotNull { p.onLine(it) }
    }

    @Test fun messageDeltaAccumulates() {
        val events = feed(
            """data: {"event":"message.delta","run_id":"r1","delta":"Hel"}""",
            """data: {"event":"message.delta","run_id":"r1","delta":"lo"}""",
        )
        assertEquals(listOf("Hel", "lo"), events.filterIsInstance<RunStreamEvent.Delta>().map { it.text })
    }

    @Test fun interimAndReasoningDistinct() {
        val events = feed(
            """data: {"event":"message.interim","text":"thinking out loud"}""",
            """data: {"event":"reasoning.available","text":"chain"}""",
        )
        assertTrue(events[0] is RunStreamEvent.Interim)
        assertTrue(events[1] is RunStreamEvent.Reasoning)
    }

    @Test fun toolStartedAndCompleted() {
        val events = feed(
            """data: {"event":"tool.started","tool":"terminal","preview":"ls -la"}""",
            """data: {"event":"tool.completed","tool":"terminal","duration":0.4,"error":false}""",
            """data: {"event":"tool.completed","tool":"web_search","duration":1.2,"error":true}""",
        )
        val started = events[0] as RunStreamEvent.ToolStarted
        assertEquals("terminal", started.tool)
        assertEquals("ls -la", started.preview)
        assertFalse((events[1] as RunStreamEvent.ToolCompleted).isError)
        assertTrue((events[2] as RunStreamEvent.ToolCompleted).isError)
    }

    @Test fun approvalRequestPreservesChoicesAndCommand() {
        val events = feed(
            """data: {"event":"approval.request","run_id":"r1","tool":"terminal","command":"rm -rf x","choices":["once","session","always","deny"],"request_id":"req_9"}""",
        )
        val ap = (events.single() as RunStreamEvent.ApprovalRequest).approval
        assertEquals(listOf("once", "session", "always", "deny"), ap.choices)
        assertEquals("terminal", ap.tool)
        assertEquals("rm -rf x", ap.command)
        assertEquals("req_9", ap.requestId)
    }

    @Test fun approvalDefaultsChoicesWhenMissing() {
        val events = feed("""data: {"event":"approval.request","run_id":"r1"}""")
        assertEquals(listOf("once", "deny"), (events.single() as RunStreamEvent.ApprovalRequest).approval.choices)
    }

    @Test fun runCompletedIsTerminalWithOutput() {
        val events = feed(
            """data: {"event":"run.completed","run_id":"r1","output":"done","usage":{}}""",
        )
        val term = events.single() as RunStreamEvent.Terminal
        assertEquals("completed", term.status)
        assertEquals("done", term.output)
    }

    @Test fun runFailedCarriesError() {
        val term = feed("""data: {"event":"run.failed","run_id":"r1","error":"boom"}""")
            .single() as RunStreamEvent.Terminal
        assertEquals("failed", term.status)
        assertEquals("boom", term.error)
    }

    @Test fun nonSettledRunEventsAreNotTerminal() {
        // run.stopping / run.steered / approval.responded are lifecycle noise, not terminal.
        val events = feed(
            """data: {"event":"run.stopping","run_id":"r1"}""",
            """data: {"event":"run.steered","run_id":"r1","accepted":true}""",
        )
        assertTrue(events.all { it is RunStreamEvent.Ignored })
    }

    @Test fun keepaliveAndStreamClosedCommentsYieldNothing() {
        assertNull(parser().onLine(": keepalive"))
        assertNull(parser().onLine(": stream closed"))
        assertNull(parser().onLine(""))
    }

    @Test fun doneSentinelDecodes() {
        assertTrue(feed("data: [DONE]").single() is RunStreamEvent.Done)
    }

    @Test fun malformedJsonDegradesToIgnoredNotCrash() {
        assertTrue(feed("data: {not valid json").single() is RunStreamEvent.Ignored)
    }

    @Test fun unknownEventIsIgnored() {
        assertTrue(feed("""data: {"event":"subagent.start","goal":"x"}""").single() is RunStreamEvent.Ignored)
    }
}
