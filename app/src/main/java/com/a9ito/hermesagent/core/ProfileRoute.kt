package com.a9ito.hermesagent.core

/**
 * Pure, Android-free helpers for Hermes multi-profile routing.
 *
 * A Hermes gateway with `gateway.multiplex_profiles` serves secondary profiles
 * under a `/p/<profile>/...` URL prefix on the same listener; the primary
 * ("default") profile answers at the bare root with no prefix. The server only
 * exposes this ingress, not a way to ENUMERATE profiles, so the profile name is
 * entered by the user (manual entry) rather than discovered.
 *
 * Routing is just URL composition: prefix `/p/<profile>/` onto the normalized
 * base URL and every REST call + SSE stream (which all derive their URL from the
 * same base) targets that profile with no per-endpoint change. Kept pure so the
 * compose/validate rules are unit-tested on the plain JVM.
 */
object ProfileRoute {

    /** The implicit primary profile, routed at the bare root with no `/p/` prefix. */
    const val DEFAULT = "default"

    private val VALID = Regex("^[A-Za-z0-9._-]+$")

    /**
     * Normalize a user-entered profile name. Trims whitespace; treats blank and
     * the literal "default" (any case) as [DEFAULT]. Returns null when the name
     * is non-blank but contains characters a profile dir name can't have (so the
     * UI can reject it) — [DEFAULT] itself is always valid and never null.
     */
    fun normalize(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || t.equals(DEFAULT, ignoreCase = true)) return DEFAULT
        return if (VALID.matches(t)) t else null
    }

    /** True when [profile] names a real secondary profile (not blank/default/invalid). */
    fun isSecondary(profile: String?): Boolean {
        val n = normalize(profile)
        return n != null && n != DEFAULT
    }

    /**
     * Compose the effective base URL for [profile] against an already-normalized,
     * trailing-slash [baseUrl] (as produced by [UrlNormalizer]).
     *
     *  - default / blank / invalid -> [baseUrl] unchanged (bare root).
     *  - a secondary profile       -> `<baseUrl>p/<profile>/`.
     *
     * The result keeps the guaranteed trailing slash so Retrofit's `baseUrl` and
     * the streamers' `trimEnd('/') + "/path"` both compose cleanly.
     */
    fun effectiveBaseUrl(baseUrl: String, profile: String?): String {
        if (baseUrl.isBlank()) return baseUrl
        if (!isSecondary(profile)) return baseUrl
        val name = normalize(profile) ?: return baseUrl
        val withSlash = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return "${withSlash}p/$name/"
    }
}
