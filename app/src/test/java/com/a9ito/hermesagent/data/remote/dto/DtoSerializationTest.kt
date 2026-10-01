package com.a9ito.hermesagent.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    @Test fun healthDetailedParsesSnakeCaseAndToleratesUnknownKeys() {
        val payload = """
            {"status":"ready","readiness":{"status":"ready"},"platform":"hermes-agent",
             "version":"0.20.0","gateway_state":"idle",
             "platforms":{"discord":{"connected":true},"telegram":{"state":"connected"}},
             "active_agents":2,"gateway_busy":false,"exit_reason":null,
             "updated_at":"2026-09-27T05:00:00Z","pid":1234}
        """.trimIndent()
        val h = json.decodeFromString(HealthDetailedDto.serializer(), payload)
        assertEquals("ready", h.status)
        assertEquals("ready", h.readiness?.status)
        assertEquals("idle", h.gatewayState)
        assertEquals(2, h.activeAgents)
        assertEquals(false, h.gatewayBusy)
        assertEquals("0.20.0", h.version)
        assertEquals(listOf("discord", "telegram"), h.platforms?.keys?.sorted())
    }

    @Test fun modelsResponsePrimaryId() {
        val payload = """{"object":"list","data":[{"id":"hermes-agent","object":"model","owned_by":"hermes"}]}"""
        val m = json.decodeFromString(ModelsResponse.serializer(), payload)
        assertEquals("hermes-agent", m.primaryModelId())
    }

    @Test fun modelsResponseEmptyIsNull() {
        val m = json.decodeFromString(ModelsResponse.serializer(), """{"object":"list","data":[]}""")
        assertNull(m.primaryModelId())
    }

    @Test fun chatCompletionFirstText() {
        val payload = """
            {"id":"chatcmpl-1","object":"chat.completion","choices":[
             {"index":0,"message":{"role":"assistant","content":"Halo Gito!"},"finish_reason":"stop"}]}
        """.trimIndent()
        val c = json.decodeFromString(ChatCompletionResponse.serializer(), payload)
        assertEquals("Halo Gito!", c.firstText())
    }

    @Test fun chatCompletionEmptyChoicesGivesEmptyText() {
        val c = json.decodeFromString(ChatCompletionResponse.serializer(), """{"id":"x","choices":[]}""")
        assertEquals("", c.firstText())
    }

    @Test fun streamingChunkDeltaContent() {
        val payload = """{"id":"x","choices":[{"index":0,"delta":{"content":"Ha"},"finish_reason":null}]}"""
        val chunk = json.decodeFromString(ChatCompletionChunk.serializer(), payload)
        assertEquals("Ha", chunk.choices.first().delta?.content)
    }

    @Test fun streamingRoleOnlyDeltaHasNullContent() {
        val payload = """{"choices":[{"delta":{"role":"assistant"}}]}"""
        val chunk = json.decodeFromString(ChatCompletionChunk.serializer(), payload)
        assertNull(chunk.choices.first().delta?.content)
    }

    @Test fun requestEncodesStreamAndRole() {
        val req = ChatCompletionRequest(messages = listOf(ChatMessageDto("user", "hi")), stream = true)
        val encoded = json.encodeToString(ChatCompletionRequest.serializer(), req)
        assertTrue(encoded.contains("\"stream\":true"))
        assertTrue(encoded.contains("\"role\":\"user\""))
        assertTrue(encoded.contains("\"model\":\"hermes-agent\""))
    }

    // A picked model is sent WITH its provider so /v1/chat/completions switches
    // reliably even when the instance leaves direct_model_requests off.
    @Test fun requestEncodesProviderWhenSet() {
        val req = ChatCompletionRequest(
            model = "gpt-5", messages = listOf(ChatMessageDto("user", "hi")), stream = false, provider = "openai",
        )
        val encoded = json.encodeToString(ChatCompletionRequest.serializer(), req)
        assertTrue(encoded.contains("\"model\":\"gpt-5\""))
        assertTrue(encoded.contains("\"provider\":\"openai\""))
    }

    // Default (no provider) must OMIT the field entirely under explicitNulls=false,
    // so a plain request stays byte-for-byte the OpenAI-standard shape.
    @Test fun requestOmitsProviderWhenNull() {
        val req = ChatCompletionRequest(messages = listOf(ChatMessageDto("user", "hi")))
        val encoded = json.encodeToString(ChatCompletionRequest.serializer(), req)
        assertFalse(encoded.contains("provider"))
    }

    // The server's PATCH /api/sessions/{id} rejects a null pinned/archived with
    // "'pinned' must be a boolean" (400). With explicitNulls=false the unset
    // fields must be OMITTED entirely, not serialized as null — this pins that.
    @Test fun patchPinnedOnlyOmitsTitleAndArchived() {
        val encoded = json.encodeToString(PatchSessionRequest.serializer(), PatchSessionRequest(pinned = true))
        assertEquals("""{"pinned":true}""", encoded)
    }

    @Test fun patchArchivedOnlyOmitsTitleAndPinned() {
        val encoded = json.encodeToString(PatchSessionRequest.serializer(), PatchSessionRequest(archived = true))
        assertEquals("""{"archived":true}""", encoded)
    }

    @Test fun patchTitleOnlyOmitsFlags() {
        val encoded = json.encodeToString(PatchSessionRequest.serializer(), PatchSessionRequest(title = "renamed"))
        assertEquals("""{"title":"renamed"}""", encoded)
    }

    // The Runs picker sends a picked model as a BARE model (no provider): the
    // native /v1/runs endpoint always honors it. Encode it when set, omit it when
    // null so a default run stays {"input":...}.
    @Test fun createRunEncodesModelWhenSet() {
        val encoded = json.encodeToString(CreateRunRequest.serializer(), CreateRunRequest(input = "go", model = "hermes-4"))
        assertTrue(encoded.contains(""""input":"go""""))
        assertTrue(encoded.contains(""""model":"hermes-4""""))
    }

    @Test fun createRunOmitsModelWhenNull() {
        val encoded = json.encodeToString(CreateRunRequest.serializer(), CreateRunRequest(input = "go"))
        assertFalse(encoded.contains("model"))
        assertFalse(encoded.contains("session_id"))
    }
}
