package com.a9ito.hermesagent.core

/**
 * Immutable, validated connection configuration the app needs to talk to a
 * Hermes API server. [baseUrl] is already normalized (trailing slash, scheme,
 * effective port) by [UrlNormalizer]. [token] is the raw Bearer token — it must
 * never be written to logs. [profile] selects a Hermes multiplex profile: the
 * default profile routes at the bare root, a secondary profile routes under
 * `/p/<profile>/` (see [ProfileRoute]).
 */
data class ConnectionConfig(
    val baseUrl: String,
    val token: String,
    val profile: String = ProfileRoute.DEFAULT,
) {
    /** True when both a base URL and a token are present — the chat gate. */
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()

    /**
     * The base URL every request/stream actually targets, with the multiplex
     * profile prefix applied. Equals [baseUrl] for the default profile.
     */
    val effectiveBaseUrl: String
        get() = ProfileRoute.effectiveBaseUrl(baseUrl, profile)

    /**
     * Deliberately redacts the token so a ConnectionConfig can never leak it via
     * an accidental log/toString call.
     */
    override fun toString(): String =
        "ConnectionConfig(baseUrl=$baseUrl, profile=$profile, token=***redacted***)"

    companion object {
        val EMPTY = ConnectionConfig(baseUrl = "", token = "", profile = ProfileRoute.DEFAULT)
    }
}
