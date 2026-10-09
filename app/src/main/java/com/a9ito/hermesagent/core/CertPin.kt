package com.a9ito.hermesagent.core

import java.security.MessageDigest
import java.util.Base64

/**
 * Pure, Android-free parser/validator for TLS certificate pins.
 *
 * A pin is one SPKI SHA-256 value in the OkHttp `CertificatePinner` format,
 * `sha256/<base64>`. The app obtains it by trust-on-first-use: a Settings button
 * captures the live server certificate's public-key hash (see
 * `CertificateProbe`), which [spkiPin] computes from the certificate's
 * SubjectPublicKeyInfo DER. When at least one valid pin is stored AND the
 * connection is HTTPS, the networking layer enforces it: a server whose
 * leaf/intermediate public-key hash is not in the set is rejected before any
 * request (and the token) is sent. No pins means normal TLS (no pinning), so
 * this is strictly opt-in and never weakens the default.
 *
 * This layer only parses, computes, and format-validates (so the rules are
 * unit-tested on the JVM); the actual pin check is done by OkHttp's
 * `CertificatePinner` in the repository. `java.util.Base64` and
 * `java.security.MessageDigest` are JVM/Android-shared (API 26+), so no Android
 * import is needed and the pin maths is host-testable.
 */
object CertPin {

    /**
     * Compute the OkHttp pin (`sha256/<base64>`) for a certificate from its
     * SubjectPublicKeyInfo DER (`X509Certificate.publicKey.encoded`). This is
     * exactly what `CertificatePinner.pin(certificate)` produces, so a pin
     * captured here matches what the pinner later enforces. Pure: SHA-256 of the
     * SPKI bytes, Base64-encoded, with the `sha256/` scheme prefix.
     */
    fun spkiPin(spkiDer: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(spkiDer)
        return "sha256/" + Base64.getEncoder().encodeToString(digest)
    }

    /** A pin is `sha256/` + base64 of a 32-byte SHA-256 digest (44 chars, one '='). */
    private val SHA256_PIN = Regex("^sha256/[A-Za-z0-9+/]{43}=$")

    sealed interface Result {
        /** No pins entered: normal TLS, pinning disabled. */
        data object Empty : Result
        /** One or more valid, de-duplicated pins (each `sha256/<base64>`). */
        data class Ok(val pins: List<String>) : Result
        /** [bad] is the first token that is not a valid pin. */
        data class Invalid(val bad: String) : Result
    }

    /**
     * Parse free-form pin input. Tokens are separated by any whitespace, newline,
     * comma, or semicolon, so a user can paste one per line or space-separated.
     * Each token must match the `sha256/<base64-32-bytes>` shape exactly.
     */
    fun parse(raw: String?): Result {
        val tokens = raw?.split(*SEPARATORS)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (tokens.isEmpty()) return Result.Empty
        val seen = LinkedHashSet<String>()
        for (t in tokens) {
            if (!isValidPin(t)) return Result.Invalid(t)
            seen.add(t)
        }
        return Result.Ok(seen.toList())
    }

    /** True when [token] is a structurally valid `sha256/<base64>` pin (32-byte digest). */
    fun isValidPin(token: String): Boolean {
        if (!SHA256_PIN.matches(token)) return false
        return try {
            // Confirm the base64 really decodes to a 32-byte SHA-256 digest, not
            // just a 44-char base64-shaped string.
            Base64.getDecoder().decode(token.removePrefix("sha256/")).size == 32
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** Convenience: the valid pin list, or empty for blank/invalid input. */
    fun pinsOrEmpty(raw: String?): List<String> = when (val r = parse(raw)) {
        is Result.Ok -> r.pins
        else -> emptyList()
    }

    private val SEPARATORS = charArrayOf(' ', '\t', '\n', '\r', ',', ';')
}
