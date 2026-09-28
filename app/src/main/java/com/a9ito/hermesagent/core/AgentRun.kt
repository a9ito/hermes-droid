package com.a9ito.hermesagent.core

/**
 * Domain view of a durable agent run (POST /v1/runs + GET /v1/runs/{id}). Pure /
 * Android-free so it can be built and asserted in host unit tests.
 *
 * A run is a one-shot agent task the app can watch live (tool events, streamed
 * answer) and control (stop, steer, approve tool calls). [status] drives which
 * controls are offered; terminal statuses end the live stream.
 */
data class AgentRun(
    val runId: String,
    val status: Status,
    val output: String? = null,
    val error: String? = null,
    val sessionId: String? = null,
    val model: String? = null,
) {
    enum class Status {
        QUEUED, RUNNING, WAITING_FOR_APPROVAL, STOPPING,
        COMPLETED, FAILED, CANCELLED, INTERRUPTED, UNKNOWN;

        val isTerminal: Boolean
            get() = this == COMPLETED || this == FAILED || this == CANCELLED || this == INTERRUPTED

        /** Steer is only accepted mid-run (the server 409s otherwise). */
        val canSteer: Boolean get() = this == RUNNING
        /** Stop is meaningful until the run settles. */
        val canStop: Boolean get() = !isTerminal && this != STOPPING
        val isApprovalPending: Boolean get() = this == WAITING_FOR_APPROVAL

        companion object {
            fun fromWire(raw: String?): Status = when (raw?.lowercase()) {
                "queued" -> QUEUED
                "running" -> RUNNING
                "waiting_for_approval" -> WAITING_FOR_APPROVAL
                "stopping" -> STOPPING
                "completed" -> COMPLETED
                "failed" -> FAILED
                "cancelled" -> CANCELLED
                "interrupted" -> INTERRUPTED
                else -> UNKNOWN
            }
        }
    }
}

/**
 * A pending tool-approval prompt surfaced by an ``approval.request`` run event.
 * [choices] are the exact tokens the server will accept back (once/session/
 * always/deny, or once/deny for room-scoped), so the UI renders one button per
 * choice rather than hardcoding them.
 */
data class RunApproval(
    val choices: List<String>,
    val tool: String? = null,
    val command: String? = null,
    val requestId: String? = null,
)
