package com.a9ito.hermesagent.core

/**
 * Domain view of GET /v1/capabilities. Pure / Android-free so it can be built
 * and asserted in host unit tests. The app consults this to adapt to the
 * connected instance: features it doesn't advertise are hidden rather than
 * offered and failing at call time.
 *
 * [knownReachable] distinguishes "instance says it supports X" from "we never
 * got a capabilities doc" (older gateway without the endpoint, or not yet
 * fetched). When capabilities are unknown, callers fall back to [baseline],
 * which assumes only the long-stable surface so the app stays usable against
 * gateways that predate /v1/capabilities.
 */
data class Capabilities(
    val knownReachable: Boolean,
    val model: String? = null,
    val authRequired: Boolean = false,
    val runtimeMode: String? = null,
    val toolExecution: String? = null,
    val runSubmission: Boolean = false,
    val runStop: Boolean = false,
    val runSteer: Boolean = false,
    val runApproval: Boolean = false,
    val runEventsSse: Boolean = false,
    val sessionChat: Boolean = false,
    val sessionChatStreaming: Boolean = false,
    val sessionFork: Boolean = false,
    val sessionModelLock: Boolean = false,
    val modelOptions: Boolean = false,
    val skillsApi: Boolean = false,
    val artifactsUpload: Boolean = false,
    val artifactsDownload: Boolean = false,
    /** Endpoint route names advertised in the capabilities doc (for exact detection). */
    val endpointNames: Set<String> = emptySet(),
) {
    /** True when the instance exposes the full run-control trio the app needs to drive a run. */
    val supportsRunControl: Boolean
        get() = runSubmission && runStop && runEventsSse

    val supportsArtifacts: Boolean get() = artifactsUpload && artifactsDownload

    companion object {
        /**
         * Conservative assumptions for a gateway whose capabilities we couldn't
         * read. Only the surface that has shipped since the first API server is
         * assumed present; anything newer stays hidden until proven available.
         */
        fun baseline(): Capabilities = Capabilities(
            knownReachable = false,
            sessionChat = true,
            sessionChatStreaming = true,
            skillsApi = true,
        )
    }
}
