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
}
