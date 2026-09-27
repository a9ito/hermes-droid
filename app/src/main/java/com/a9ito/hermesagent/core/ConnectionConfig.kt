package com.a9ito.hermesagent.core

/**
 * Immutable, validated connection configuration the app needs to talk to a
 * Hermes API server. [baseUrl] is already normalized (trailing slash, scheme,
 * effective port) by [UrlNormalizer]. [token] is the raw Bearer token — it must
 * never be written to logs.
 */
data class ConnectionConfig(
    val baseUrl: String,
    val token: String,
) {
    /** True when both a base URL and a token are present — the chat gate. */
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()

    /**
     * Deliberately redacts the token so a ConnectionConfig can never leak it via
     * an accidental log/toString call.
     */
    override fun toString(): String =
        "ConnectionConfig(baseUrl=$baseUrl, token=***redacted***)"

    companion object {
        val EMPTY = ConnectionConfig(baseUrl = "", token = "")
    }
}
