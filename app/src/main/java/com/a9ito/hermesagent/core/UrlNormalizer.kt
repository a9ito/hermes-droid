package com.a9ito.hermesagent.core

/**
 * Pure, Android-free URL normalization for the Hermes API server base URL.
 *
 * Kept dependency-free on purpose so it can be unit-tested on the plain JVM
 * (see UrlNormalizerTest). All parsing is manual rather than using
 * android.net.Uri, which is unavailable in host unit tests.
 */
object UrlNormalizer {

    /** Result of trying to build a base URL from user-entered host + port. */
    sealed interface Result {
        data class Ok(val baseUrl: String) : Result
        data object EmptyHost : Result
        data class InvalidPort(val raw: String) : Result
        data class InvalidHost(val raw: String) : Result
    }

    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /**
     * Build a normalized, trailing-slash base URL from a host/URL string and an
     * optional port.
     *
     * Rules:
     *  - Blank host -> [Result.EmptyHost].
     *  - No scheme -> `http://` is assumed.
     *  - A port already present in the host wins; otherwise [port] (if given) is
     *    appended.
     *  - Any path in the host is preserved; a trailing slash is guaranteed.
     *  - [port], when provided, must be an integer in 1..65535.
     */
    fun normalize(host: String, port: String?): Result {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) return Result.EmptyHost

        val portTrimmed = port?.trim().orEmpty()
        val explicitPort: Int? = if (portTrimmed.isEmpty()) {
            null
        } else {
            val parsed = portTrimmed.toIntOrNull()
            if (parsed == null || parsed !in 1..65535) return Result.InvalidPort(portTrimmed)
            parsed
        }

        // Ensure a scheme so authority parsing is unambiguous.
        val withScheme = if (SCHEME_REGEX.containsMatchIn(trimmedHost)) {
            trimmedHost
        } else {
            "http://$trimmedHost"
        }

        val schemeSep = withScheme.indexOf("://")
        val scheme = withScheme.substring(0, schemeSep).lowercase()
        if (scheme != "http" && scheme != "https") return Result.InvalidHost(trimmedHost)

        val afterScheme = withScheme.substring(schemeSep + 3)
        if (afterScheme.isEmpty()) return Result.InvalidHost(trimmedHost)

        // Split authority from path (first '/').
        val slashIdx = afterScheme.indexOf('/')
        val authority = if (slashIdx == -1) afterScheme else afterScheme.substring(0, slashIdx)
        val path = if (slashIdx == -1) "" else afterScheme.substring(slashIdx)
        if (authority.isEmpty()) return Result.InvalidHost(trimmedHost)

        // Detect a port already in the authority (guard against IPv6 "[::1]" — no port support needed here).
        val hasBracket = authority.startsWith("[")
        val authorityHostPart: String
        val authorityPort: Int?
        if (hasBracket) {
            // Bare IPv6 without a port; leave as-is, ignore the port field.
            authorityHostPart = authority
            authorityPort = null
        } else {
            val colonIdx = authority.lastIndexOf(':')
            if (colonIdx != -1) {
                val portSubstring = authority.substring(colonIdx + 1)
                val p = portSubstring.toIntOrNull()
                if (p == null || p !in 1..65535) return Result.InvalidHost(trimmedHost)
                authorityHostPart = authority.substring(0, colonIdx)
                authorityPort = p
            } else {
                authorityHostPart = authority
                authorityPort = null
            }
        }
        if (authorityHostPart.isEmpty()) return Result.InvalidHost(trimmedHost)

        val effectivePort = authorityPort ?: explicitPort
        val rebuiltAuthority = if (effectivePort != null && !hasBracket) {
            "$authorityHostPart:$effectivePort"
        } else {
            authority
        }

        val normalizedPath = when {
            path.isEmpty() -> "/"
            path.endsWith("/") -> path
            else -> "$path/"
        }

        return Result.Ok("$scheme://$rebuiltAuthority$normalizedPath")
    }
}
