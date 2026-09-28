package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.AgentRun
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs for the /v1/runs (durable agent-run) resource. Confirmed against
 * api_server_runs.py: POST /v1/runs returns a 202 admission body
 * {run_id, status, replayed}; GET /v1/runs/{id} returns the pollable status
 * record {object, run_id, status, updated_at, output?, error?, session_id?,
 * model?, ...}. Everything but ids is nullable — the record grows fields as the
 * run progresses.
 */

/** POST /v1/runs — request body. ``input`` is the user message (string form). */
@Serializable
data class CreateRunRequest(
    val input: String,
    val model: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
)

/** 202 admission response. */
@Serializable
data class RunAdmissionDto(
    @SerialName("run_id") val runId: String,
    val status: String? = null,
    val replayed: Boolean = false,
)

/** GET /v1/runs/{id} pollable status. */
@Serializable
data class RunStatusDto(
    @SerialName("run_id") val runId: String,
    val status: String? = null,
    val output: String? = null,
    val error: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    val model: String? = null,
    @SerialName("updated_at") val updatedAt: JsonElement? = null,
    @SerialName("last_event") val lastEvent: String? = null,
)

/** POST /v1/runs/{id}/steer — body. */
@Serializable
data class SteerRequest(val input: String)

/** POST /v1/runs/{id}/approval — body. ``choice`` is one of the run's advertised choices. */
@Serializable
data class ApprovalRequestBody(
    val choice: String,
    @SerialName("request_id") val requestId: String? = null,
)

fun RunAdmissionDto.toDomain(): AgentRun =
    AgentRun(runId = runId, status = AgentRun.Status.fromWire(status))

fun RunStatusDto.toDomain(): AgentRun = AgentRun(
    runId = runId,
    status = AgentRun.Status.fromWire(status),
    output = output?.takeIf { it.isNotEmpty() },
    error = error?.takeIf { it.isNotEmpty() },
    sessionId = sessionId,
    model = model,
)
