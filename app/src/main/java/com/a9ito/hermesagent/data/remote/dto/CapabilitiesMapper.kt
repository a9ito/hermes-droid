package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.Capabilities

/**
 * Pure DTO -> domain mapper for /v1/capabilities. Unit-tested on the JVM
 * (CapabilitiesMapperTest). Endpoint presence is taken from the ``endpoints``
 * table (the authoritative route list) with a feature-flag fallback, so a
 * gateway that advertises a route but omits the matching feature flag (or vice
 * versa) is still handled.
 */
fun CapabilitiesDto.toDomain(): Capabilities {
    val f = features
    val names = endpoints.keys
    return Capabilities(
        knownReachable = true,
        model = model,
        authRequired = auth?.required ?: false,
        runtimeMode = runtime?.mode,
        toolExecution = runtime?.toolExecution,
        runSubmission = f?.runSubmission == true || "runs" in names,
        runStop = f?.runStop == true || "run_stop" in names,
        runSteer = f?.runSteer == true || "run_steer" in names,
        runApproval = f?.runApproval == true || "run_approval" in names,
        runEventsSse = f?.runEventsSse == true || "run_events" in names,
        sessionChat = f?.sessionChat == true || "session_chat" in names,
        sessionChatStreaming = f?.sessionChatStreaming == true || "session_chat_stream" in names,
        sessionFork = f?.sessionFork == true || "session_fork" in names,
        sessionModelLock = f?.sessionModelLock == true || "session_model_lock" in names,
        modelOptions = f?.modelOptions == true || "model_options" in names,
        skillsApi = f?.skillsApi == true || "skills" in names,
        artifactsUpload = "artifact_upload" in names,
        artifactsDownload = "artifact_download" in names,
        endpointNames = names,
    )
}
