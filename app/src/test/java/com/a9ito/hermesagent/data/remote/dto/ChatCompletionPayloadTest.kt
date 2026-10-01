package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.ChatAttachment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior contract for the /v1/chat/completions request builder. The endpoint
 * (and real OpenAI) accept message content as EITHER a plain string or a parts
 * array of text + image_url; these pin that we emit a plain string for text-only
 * turns (so prompt caching sees the native shape) and the parts array only on the
 * final user turn when images are attached.
 */
class ChatCompletionPayloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun build(
        messages: List<ChatMessageDto>,
        model: String? = null,
        provider: String? = null,
        attachments: List<ChatAttachment> = emptyList(),
        stream: Boolean = false,
    ): JsonObject = ChatCompletionPayload.build(model, messages, stream, provider, attachments).jsonObject

    private fun img(mime: String = "image/png", data: String = "AAAA") =
        ChatAttachment(mimeType = mime, base64Data = data)

    @Test fun defaultsModelAndEncodesStream() {
        val obj = build(listOf(ChatMessageDto("user", "hi")), stream = true)
        assertEquals("hermes-agent", obj["model"]!!.jsonPrimitive.content)
        assertEquals(true, obj["stream"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun textOnlyKeepsStringContent() {
        // No attachments: every message content stays a plain string (cache-friendly).
        val obj = build(listOf(ChatMessageDto("user", "hello")))
        val msgs = obj["messages"] as JsonArray
        assertTrue(msgs[0].jsonObject["content"] is JsonPrimitive)
        assertEquals("hello", msgs[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    // A picked model is sent WITH its provider so the switch is honored even when
    // the instance leaves direct_model_requests off; a plain turn omits provider.
    @Test fun encodesProviderWhenSet() {
        val obj = build(listOf(ChatMessageDto("user", "hi")), model = "gpt-5", provider = "openai")
        assertEquals("gpt-5", obj["model"]!!.jsonPrimitive.content)
        assertEquals("openai", obj["provider"]!!.jsonPrimitive.content)
    }

    @Test fun omitsProviderWhenNull() {
        val encoded = ChatCompletionPayload.encode(json, null, listOf(ChatMessageDto("user", "hi")), stream = false, provider = null)
        assertFalse(encoded.contains("provider"))
    }

    @Test fun imageOnFinalUserTurnEmitsPartsArray() {
        val obj = build(
            listOf(ChatMessageDto("user", "earlier"), ChatMessageDto("assistant", "ok"), ChatMessageDto("user", "look")),
            attachments = listOf(img()),
        )
        val msgs = obj["messages"] as JsonArray
        // Earlier turns stay plain strings.
        assertTrue(msgs[0].jsonObject["content"] is JsonPrimitive)
        assertTrue(msgs[1].jsonObject["content"] is JsonPrimitive)
        // Final user turn becomes a parts array: text + image_url.
        val parts = msgs[2].jsonObject["content"] as JsonArray
        assertEquals(2, parts.size)
        assertEquals("text", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("look", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("image_url", parts[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "data:image/png;base64,AAAA",
            parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content,
        )
    }

    @Test fun imageWithNoCaptionOmitsTextPart() {
        val obj = build(listOf(ChatMessageDto("user", "")), attachments = listOf(img()))
        val parts = (obj["messages"] as JsonArray)[0].jsonObject["content"] as JsonArray
        assertEquals(1, parts.size)
        assertEquals("image_url", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun nonImageAttachmentsDropped() {
        val obj = build(
            listOf(ChatMessageDto("user", "x")),
            attachments = listOf(ChatAttachment(mimeType = "application/pdf", base64Data = "Zm9v")),
        )
        // No image parts survive, so the final turn stays a plain string.
        assertTrue((obj["messages"] as JsonArray)[0].jsonObject["content"] is JsonPrimitive)
    }
}
