package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.SessionMessage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior contract for DTO -> domain mapping: title fallback, fork detection,
 * and multimodal content flattening. These are the pieces the Sessions UI reads.
 */
class SessionMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun summaryUsesTitleWhenPresent() {
        val dto = SessionDto(id = "abc123def456", title = "My chat", messageCount = 4)
        val s = dto.toSummary()
        assertEquals("My chat", s.title)
        assertEquals(4, s.messageCount)
        assertFalse(s.isFork)
    }

    @Test fun summaryFallsBackToShortIdWhenUntitled() {
        val dto = SessionDto(id = "abcdef1234567890", title = null)
        // Falls back to a stable, non-blank label derived from the id prefix.
        val title = dto.toSummary().title
        assertTrue(title.isNotBlank())
        assertTrue(title.contains("abcdef12"))
    }

    @Test fun forkDetectedFromParent() {
        val dto = SessionDto(id = "child", parentSessionId = "parent")
        assertTrue(dto.toSummary().isFork)
    }

    @Test fun plainStringContentExtracted() {
        val el = json.parseToJsonElement(""""just text"""")
        assertEquals("just text", extractMessageText(el))
    }

    @Test fun multimodalArrayFlattensTextAndSummarizesImages() {
        val el = json.parseToJsonElement(
            """[{"type":"text","text":"look at this"},{"type":"image_url","image_url":{"url":"x"}}]"""
        )
        val text = extractMessageText(el)
        assertTrue(text.contains("look at this"))
        assertTrue(text.contains("[image]"))
    }

    @Test fun nullContentIsEmptyString() {
        assertEquals("", extractMessageText(null))
    }

    @Test fun displayMessagesDropSystemAndEmpty() {
        val dtos = listOf(
            SessionMessageDto(id = 1, role = "system", content = json.parseToJsonElement(""""sys"""")),
            SessionMessageDto(id = 2, role = "user", content = json.parseToJsonElement(""""hi"""")),
            SessionMessageDto(id = 3, role = "assistant", content = json.parseToJsonElement("""""""")),
        )
        val shown = dtos.toDisplayMessages()
        assertEquals(1, shown.size)
        assertEquals(SessionMessage.Role.USER, shown.single().role)
        assertEquals("hi", shown.single().text)
    }

    @Test fun reasoningPreferredOverReasoningContentWhenBothPresent() {
        val dto = SessionMessageDto(id = 1, role = "assistant", reasoning = "short", reasoningContent = "long fallback")
        assertEquals("short", dto.toDomain().reasoning)
    }

    @Test fun reasoningFallsBackToReasoningContent() {
        val dto = SessionMessageDto(id = 1, role = "assistant", reasoning = null, reasoningContent = "the thinking")
        assertEquals("the thinking", dto.toDomain().reasoning)
    }

    @Test fun blankReasoningBecomesNull() {
        val dto = SessionMessageDto(id = 1, role = "assistant", reasoning = "   ", reasoningContent = "")
        assertNull(dto.toDomain().reasoning)
    }

    @Test fun assistantWithReasoningButBlankTextIsKept() {
        // A reasoning-only assistant row must survive the display filter so the
        // collapsible thinking is still reachable in the transcript.
        val dtos = listOf(
            SessionMessageDto(id = 1, role = "assistant", content = json.parseToJsonElement("\"\""), reasoning = "I thought hard"),
        )
        val shown = dtos.toDisplayMessages()
        assertEquals(1, shown.size)
        assertEquals("I thought hard", shown.single().reasoning)
    }
}
