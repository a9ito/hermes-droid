package com.a9ito.hermesagent.core

/**
 * Domain snapshot of the connected instance, mapped from GET /health/detailed +
 * GET /v1/models. Pure/Android-free so it can be constructed and asserted in
 * host unit tests. All fields optional — the API reports bounded status and
 * older gateways omit some.
 */
data class InstanceStatus(
    val reachable: Boolean,
    val overallStatus: String? = null,
    val readiness: String? = null,
    val gatewayState: String? = null,
    val busy: Boolean? = null,
    val activeAgents: Int? = null,
    val model: String? = null,
    val version: String? = null,
    val connectedPlatforms: List<String> = emptyList(),
    val updatedAt: String? = null,
)

/**
 * Classification of a failed call, so ViewModels can pick a localized message
 * without parsing exceptions or HTTP internals themselves.
 */
enum class ErrorKind {
    NO_CONNECTION,
    AUTH,
    NETWORK,
    TIMEOUT,
    SERVER_ERROR,
    UNEXPECTED,
}
