package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.Capabilities
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior contract for /v1/capabilities parsing + domain mapping: the real
 * server payload shape, endpoint-vs-flag detection, the run-control/artifacts
 * composite gates, and the baseline used when the doc is absent.
 */
class CapabilitiesMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Trimmed but faithful to api_server.py::_handle_capabilities output. */
    private val realPayload = """
        {"object":"hermes.api_server.capabilities","platform":"hermes-agent","model":"hermes-agent",
         "auth":{"type":"bearer","required":true},
         "runtime":{"mode":"server_agent","tool_execution":"server","split_runtime":false,
                    "description":"server-side agent"},
         "features":{"chat_completions":true,"chat_completions_streaming":true,"responses_api":true,
                     "run_submission":true,"run_status":true,"run_events_sse":true,"run_stop":true,
                     "run_steer":true,"run_approval_response":true,"approval_events":true,
                     "session_chat":true,"session_chat_streaming":true,"session_fork":true,
                     "session_model_lock":true,"model_options":true,"skills_api":true,
                     "reasoning_streaming":true,"audio_api":false,"realtime_voice":false,
                     "runs_idempotency":{"supported":true},
                     "browser_extension_control":{"enabled":false}},
         "endpoints":{"runs":{"method":"POST","path":"/v1/runs"},
                      "run_stop":{"method":"POST","path":"/v1/runs/{run_id}/stop"},
                      "run_events":{"method":"GET","path":"/v1/runs/{run_id}/events"},
                      "run_steer":{"method":"POST","path":"/v1/runs/{run_id}/steer"},
                      "run_approval":{"method":"POST","path":"/v1/runs/{run_id}/approval"},
                      "artifact_upload":{"method":"POST","path":"/v1/artifacts/upload"},
                      "artifact_download":{"method":"GET","path":"/v1/artifacts/download/{artifact_id}"},
                      "session_fork":{"method":"POST","path":"/api/sessions/{session_id}/fork"}}}
    """.trimIndent()

    @Test fun parsesRealServerCapabilities() {
        val caps = json.decodeFromString(CapabilitiesDto.serializer(), realPayload).toDomain()
        assertTrue(caps.knownReachable)
        assertEquals("hermes-agent", caps.model)
        assertTrue(caps.authRequired)
        assertEquals("server_agent", caps.runtimeMode)
        assertTrue(caps.supportsRunControl)
        assertTrue(caps.runSteer)
        assertTrue(caps.runApproval)
        assertTrue(caps.supportsArtifacts)
        assertTrue(caps.sessionModelLock)
    }

    @Test fun runControlNeedsAllThreeParts() {
        // submit + stop but no events stream -> not full control.
        val partial = CapabilitiesDto(
            features = FeaturesDto(runSubmission = true, runStop = true, runEventsSse = false),
        ).toDomain()
        assertFalse(partial.supportsRunControl)
    }

    @Test fun endpointPresenceAloneEnablesDetection() {
        // Flags omitted, but the endpoints table lists the routes.
        val payload = """
            {"features":{},"endpoints":{"runs":{"method":"POST","path":"/v1/runs"},
             "run_stop":{"method":"POST","path":"/x"},"run_events":{"method":"GET","path":"/y"}}}
        """.trimIndent()
        val caps = json.decodeFromString(CapabilitiesDto.serializer(), payload).toDomain()
        assertTrue(caps.supportsRunControl)
    }

    @Test fun artifactsNeedBothDirections() {
        val uploadOnly = CapabilitiesDto(
            endpoints = mapOf("artifact_upload" to EndpointDto("POST", "/v1/artifacts/upload")),
        ).toDomain()
        assertFalse(uploadOnly.supportsArtifacts)
    }

    @Test fun baselineIsConservativeAndFlaggedUnknown() {
        val base = Capabilities.baseline()
        assertFalse(base.knownReachable)
        assertTrue(base.sessionChat)          // long-stable, assumed present
        assertTrue(base.skillsApi)
        assertFalse(base.supportsRunControl)  // newer, hidden until proven
        assertFalse(base.supportsArtifacts)
        assertFalse(base.modelOptions)
    }

    @Test fun toleratesUnknownFutureFlags() {
        val payload = """{"features":{"teleportation":true,"skills_api":true},"endpoints":{}}"""
        val caps = json.decodeFromString(CapabilitiesDto.serializer(), payload).toDomain()
        assertTrue(caps.skillsApi)
        assertTrue(caps.knownReachable)
    }
}
