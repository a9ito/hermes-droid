package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryTest {

    @Test fun startTurnAddsUserAndStreamingAssistant() {
        val (history, assistantId) = ChatHistory().startTurn("hello")
        assertEquals(2, history.messages.size)
        assertEquals(ChatMessage.Role.USER, history.messages[0].role)
        assertEquals("hello", history.messages[0].text)
        val assistant = history.messages[1]
        assertEquals(ChatMessage.Role.ASSISTANT, assistant.role)
        assertEquals(assistantId, assistant.id)
        assertTrue(assistant.streaming)
        assertEquals("", assistant.text)
    }

    @Test fun appendDeltaAccumulatesText() {
        val (h0, id) = ChatHistory().startTurn("q")
        val h1 = h0.appendDelta(id, "Hel").appendDelta(id, "lo")
        assertEquals("Hello", h1.messages.first { it.id == id }.text)
        assertTrue(h1.messages.first { it.id == id }.streaming)
    }

    @Test fun finishStopsStreaming() {
        val (h0, id) = ChatHistory().startTurn("q")
        val h1 = h0.appendDelta(id, "done").finish(id)
        val msg = h1.messages.first { it.id == id }
        assertFalse(msg.streaming)
        assertEquals("done", msg.text)
    }

    @Test fun setTextReplacesWholesale() {
        val (h0, id) = ChatHistory().startTurn("q")
        val h1 = h0.setText(id, "full reply")
        assertEquals("full reply", h1.messages.first { it.id == id }.text)
    }

    @Test fun failMarksErrorAndStops() {
        val (h0, id) = ChatHistory().startTurn("q")
        val h1 = h0.fail(id, ErrorKind.NETWORK)
        val msg = h1.messages.first { it.id == id }
        assertTrue(msg.error)
        assertFalse(msg.streaming)
        assertEquals(ErrorKind.NETWORK, msg.errorKind)
        assertEquals("", msg.text)
    }

    @Test fun idsAreUniqueAcrossTurns() {
        val (h0, firstAssistant) = ChatHistory().startTurn("one")
        val (h1, secondAssistant) = h0.finish(firstAssistant).startTurn("two")
        val ids = h1.messages.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(secondAssistant > firstAssistant)
    }

    @Test fun clearEmptiesButKeepsIdCounter() {
        val (h0, _) = ChatHistory().startTurn("one")
        val cleared = h0.clear()
        assertTrue(cleared.messages.isEmpty())
        // New turn after clear must not reuse an old id.
        val (h1, newAssistant) = cleared.startTurn("two")
        assertTrue(newAssistant >= 3)
        assertEquals(2, h1.messages.size)
    }

    @Test fun thinkingSetsOnlyWhileTextEmpty() {
        val (h0, id) = ChatHistory().startTurn("q")
        val thinking = h0.setThinking(id, true)
        assertTrue(thinking.messages.first { it.id == id }.thinking)
        // Once text arrives, a stale thinking flag can't turn back on.
        val answered = thinking.appendDelta(id, "hi")
        assertFalse(answered.messages.first { it.id == id }.thinking)
        val reattempt = answered.setThinking(id, true)
        assertFalse(reattempt.messages.first { it.id == id }.thinking)
    }

    @Test fun toolStartedThenCompletedTracksStatus() {
        val (h0, id) = ChatHistory().startTurn("q")
        val running = h0.setThinking(id, true).toolStarted(id, "terminal")
        val m1 = running.messages.first { it.id == id }
        assertFalse(m1.thinking) // a concrete tool ends the thinking state
        assertEquals(1, m1.activities.size)
        assertEquals(ToolActivity.Status.RUNNING, m1.activities[0].status)

        val done = running.toolFinished(id, "terminal", ToolActivity.Status.DONE)
        assertEquals(ToolActivity.Status.DONE, done.messages.first { it.id == id }.activities[0].status)
    }

    @Test fun duplicateToolStartIsNotDoubleCounted() {
        val (h0, id) = ChatHistory().startTurn("q")
        val once = h0.toolStarted(id, "terminal")
        val twice = once.toolStarted(id, "terminal")
        assertEquals(1, twice.messages.first { it.id == id }.activities.size)
    }

    @Test fun twoDistinctToolsBothTracked() {
        val (h0, id) = ChatHistory().startTurn("q")
        val h1 = h0.toolStarted(id, "terminal").toolStarted(id, "web_search")
            .toolFinished(id, "terminal", ToolActivity.Status.DONE)
            .toolFinished(id, "web_search", ToolActivity.Status.FAILED)
        val acts = h1.messages.first { it.id == id }.activities
        assertEquals(2, acts.size)
        assertEquals(ToolActivity.Status.DONE, acts.first { it.toolName == "terminal" }.status)
        assertEquals(ToolActivity.Status.FAILED, acts.first { it.toolName == "web_search" }.status)
    }

    @Test fun toolFinishedForUnknownToolIsNoop() {
        val (h0, id) = ChatHistory().startTurn("q")
        val h1 = h0.toolFinished(id, "never_started", ToolActivity.Status.DONE)
        assertTrue(h1.messages.first { it.id == id }.activities.isEmpty())
    }

    @Test fun commentaryAccumulatesInOrder() {
        val (h0, id) = ChatHistory().startTurn("q")
        val h1 = h0.appendCommentary(id, "first").appendCommentary(id, "second")
        assertEquals(listOf("first", "second"), h1.messages.first { it.id == id }.commentary)
    }

    @Test fun finishAndFailClearThinking() {
        val (h0, id) = ChatHistory().startTurn("q")
        val thinking = h0.setThinking(id, true)
        assertFalse(thinking.finish(id).messages.first { it.id == id }.thinking)
        assertFalse(thinking.fail(id, ErrorKind.NETWORK).messages.first { it.id == id }.thinking)
    }
}
