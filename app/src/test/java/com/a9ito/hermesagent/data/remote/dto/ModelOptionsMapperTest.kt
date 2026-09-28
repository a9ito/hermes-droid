package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.ModelOptions
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior contract for /api/model/options parsing + domain mapping. Pins the
 * real payload shape (current model/provider at TOP level, capability/pricing
 * maps keyed by model id), the current-model marking, unavailable + needs-auth
 * gating, and tolerance of the many optional keys the server may omit.
 */
class ModelOptionsMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Trimmed but faithful to inventory.py::build_models_payload output. */
    private val realPayload = """
        {"model":"hermes-4","provider":"nous",
         "providers":[
           {"slug":"nous","name":"Nous","is_current":true,"authenticated":true,"free_tier":true,
            "models":["hermes-4","hermes-4-405b"],"total_models":2,
            "featured_models":["hermes-4"],
            "unavailable_models":["hermes-4-405b"],
            "capabilities":{"hermes-4":{"fast":true,"reasoning":true,"can_disable_reasoning":true}},
            "pricing":{"hermes-4":{"input":"free","output":"free","free":true}}},
           {"slug":"openai","name":"OpenAI","is_current":false,"authenticated":false,"auth_type":"api_key",
            "key_env":"OPENAI_API_KEY","models":["gpt-5"],"total_models":1,
            "pricing":{"gpt-5":{"input":"${'$'}1.25","output":"${'$'}10.00","cache":"${'$'}0.13"}}}
         ]}
    """.trimIndent()

    private fun parse(payload: String): ModelOptions =
        json.decodeFromString(ModelOptionsResponse.serializer(), payload).toDomain()

    @Test fun mapsCurrentSelectionFromTopLevel() {
        val opts = parse(realPayload)
        assertEquals("hermes-4", opts.currentModel)
        assertEquals("nous", opts.currentProvider)
        assertFalse(opts.isEmpty)
        assertEquals(2, opts.providers.size)
    }

    @Test fun marksCurrentModelWithinProvider() {
        val nous = parse(realPayload).providers.first { it.slug == "nous" }
        assertTrue(nous.isCurrent)
        assertTrue(nous.freeTier)
        assertTrue(nous.models.first { it.id == "hermes-4" }.isCurrent)
    }

    @Test fun attachesCapabilityHintsByModelId() {
        val h4 = parse(realPayload).providers.first { it.slug == "nous" }.models.first { it.id == "hermes-4" }
        assertTrue(h4.supportsReasoning)
        assertTrue(h4.canDisableReasoning)
        assertTrue(h4.supportsFastMode)
    }

    @Test fun unavailableModelsFlagged() {
        val big = parse(realPayload).providers.first { it.slug == "nous" }.models.first { it.id == "hermes-4-405b" }
        assertTrue(big.unavailable)
    }

    @Test fun featuredIdsCaptured() {
        val nous = parse(realPayload).providers.first { it.slug == "nous" }
        assertTrue("hermes-4" in nous.featuredModelIds)
    }

    @Test fun unauthenticatedProviderNeedsAuth() {
        val openai = parse(realPayload).providers.first { it.slug == "openai" }
        assertFalse(openai.authenticated)
        assertTrue(openai.needsAuth)
    }

    @Test fun pricingStringsPreservedFreeAndPaid() {
        val opts = parse(realPayload)
        val free = opts.providers.first { it.slug == "nous" }.models.first { it.id == "hermes-4" }.pricing!!
        assertTrue(free.free)
        val paid = opts.providers.first { it.slug == "openai" }.models.first { it.id == "gpt-5" }.pricing!!
        assertEquals("\$1.25", paid.input)
        assertEquals("\$10.00", paid.output)
        assertEquals("\$0.13", paid.cache)
        assertFalse(paid.free)
    }

    @Test fun localProviderNotFlaggedNeedsAuthEvenIfUnauthenticated() {
        // auth_type "local" means no key needed; an unauthenticated local row is still usable.
        val payload = """{"providers":[{"slug":"llamacpp","name":"Local","authenticated":false,
            "auth_type":"local","models":["qwen"]}]}"""
        val local = parse(payload).providers.single()
        assertFalse(local.needsAuth)
    }

    @Test fun emptyPayloadIsEmpty() {
        assertTrue(parse("""{"providers":[]}""").isEmpty)
    }

    @Test fun blankCurrentBecomesNull() {
        val opts = parse("""{"model":"","provider":"","providers":[]}""")
        assertNull(opts.currentModel)
        assertNull(opts.currentProvider)
    }

    @Test fun flatModelIdsSkipsUnauthenticatedProviders() {
        // gpt-5 lives behind an unauthenticated provider -> excluded from the flat convenience list.
        val ids = parse(realPayload).flatModelIds()
        assertTrue("hermes-4" in ids)
        assertFalse("gpt-5" in ids)
    }

    @Test fun toleratesMissingOptionalMapsAndFutureKeys() {
        val payload = """{"model":"m","provider":"p","providers":[
            {"slug":"p","name":"P","models":["m"],"brand_new_key":123}]}"""
        val prov = parse(payload).providers.single()
        assertEquals(1, prov.models.size)
        assertNull(prov.models.single().pricing) // no pricing map -> null, no crash
        assertFalse(prov.models.single().supportsReasoning)
    }
}
