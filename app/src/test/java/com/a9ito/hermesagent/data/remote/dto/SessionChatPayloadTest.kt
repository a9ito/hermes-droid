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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior contract for the session-chat request body builder. The server's
 * _normalize_multimodal_content accepts EITHER a plain-string message (text
 * only) or a parts array of text + image_url; these tests pin that we emit the
 * native shape in each case so the endpoint never 400s on our body.
 */
class SessionChatPayloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun build(message: String, attachments: List<ChatAttachment>, model: String? = null): JsonObject =
        SessionChatPayload.build(message, attachments, model).jsonObject

    private fun img(mime: String = "image/png", data: String = "AAAA") =
        ChatAttachment(mimeType = mime, base64Data = data)

    @Test fun textOnlyCollapsesToPlainString() {
        val obj = build("hello there", emptyList())
        // message must be a plain string, not an array — matches the server's native shape.
        assertTrue(obj["message"] is JsonPrimitive)
        assertEquals("hello there", obj["message"]!!.jsonPrimitive.content)
    }

    @Test fun modelOmittedWhenNull() {
        val obj = build("hi", emptyList(), model = null)
        assertTrue("model" !in obj)
    }

    @Test fun modelIncludedWhenSet() {
        val obj = build("hi", emptyList(), model = "hermes-agent")
        assertEquals("hermes-agent", obj["model"]!!.jsonPrimitive.content)
    }

    @Test fun withImageEmitsPartsArray() {
        val obj = build("look at this", listOf(img()))
        val parts = obj["message"] as JsonArray
        assertEquals(2, parts.size)
        // Leading text part carries the caption.
        assertEquals("text", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("look at this", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        // Image part is the canonical image_url shape with a data URL.
        assertEquals("image_url", parts[1].jsonObject["type"]!!.jsonPrimitive.content)
        val url = parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertEquals("data:image/png;base64,AAAA", url)
    }

    @Test fun imageWithNoCaptionOmitsTextPart() {
        val obj = build("", listOf(img()))
        val parts = obj["message"] as JsonArray
        assertEquals(1, parts.size)
        assertEquals("image_url", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun multipleImagesAllEmitted() {
        val obj = build("two", listOf(img(data = "A"), img(mime = "image/jpeg", data = "B")))
        val parts = obj["message"] as JsonArray
        assertEquals(3, parts.size) // 1 text + 2 images
        assertEquals("data:image/jpeg;base64,B", parts[2].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test fun nonImageAttachmentsAreDropped() {
        // A non-image (defensive: loader shouldn't produce these) is filtered so
        // we never send a file part the endpoint rejects.
        val obj = build("x", listOf(ChatAttachment(mimeType = "application/pdf", base64Data = "Zm9v")))
        val parts = obj["message"] as JsonArray
        assertEquals(1, parts.size) // only the text part survives
        assertEquals("text", parts[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun encodeRoundTripsThroughJson() {
        val encoded = SessionChatPayload.encode(json, "hi", emptyList(), model = null)
        val reparsed = json.parseToJsonElement(encoded).jsonObject
        assertEquals("hi", reparsed["message"]!!.jsonPrimitive.content)
    }
}
