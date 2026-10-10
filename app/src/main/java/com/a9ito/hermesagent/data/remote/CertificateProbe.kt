package com.a9ito.hermesagent.data.remote

import com.a9ito.hermesagent.core.CertPin
import com.a9ito.hermesagent.core.CleartextPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.cert.X509Certificate

/**
 * Trust-on-first-use helper that captures a server's current TLS leaf-certificate
 * pin so the user can enable certificate pinning without computing a hash by hand.
 *
 * [probe] opens a normal, fully-validated HTTPS connection to the configured base
 * URL (so a server with an untrusted/expired certificate fails here rather than
 * being silently pinned) and returns the leaf certificate's SubjectPublicKeyInfo
 * pin in the OkHttp `sha256/<base64>` form via [CertPin.spkiPin]. That value is
 * exactly what [okhttp3.CertificatePinner] later enforces, so a captured pin and
 * an enforced pin always agree.
 *
 * This is explicitly TOFU: it trusts whatever certificate is presented at capture
 * time. It is only a convenience for obtaining the pin string; the security value
 * comes from pinning every *subsequent* connection to that captured key. It is a
 * no-op (returns [Result.NotHttps]) for a cleartext base URL, since pinning only
 * acts on TLS.
 */
object CertificateProbe {

    sealed interface Result {
        /** Captured the leaf SPKI [pin] (`sha256/<base64>`) plus a short [subject] label for display. */
        data class Ok(val pin: String, val subject: String) : Result
        /** The base URL is not https, so there is no certificate to pin. */
        data object NotHttps : Result
        /** The TLS handshake or connection failed (untrusted cert, unreachable, timeout). */
        data class Failed(val reason: String) : Result
    }

    /**
     * Open a validated TLS connection to [baseUrl] and return its leaf pin. Runs
     * the blocking OkHttp call on [Dispatchers.IO]. Uses a short-timeout client of
     * its own (no AuthInterceptor): the probe must never send the bearer token,
     * and it must validate the chain the normal way so an attacker's cert is not
     * captured as the trusted pin.
     */
    suspend fun probe(baseUrl: String): Result = withContext(Dispatchers.IO) {
        if (!baseUrl.trim().startsWith("https://", ignoreCase = true)) return@withContext Result.NotHttps
        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        try {
            // GET the health endpoint: cheap, unauthenticated, and it forces the
            // TLS handshake we need. We only read the handshake, not the body.
            val url = baseUrl.trimEnd('/') + "/health"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val certs = response.handshake?.peerCertificates.orEmpty()
                val leaf = certs.firstOrNull() as? X509Certificate
                    ?: return@withContext Result.Failed("no_certificate")
                val pin = CertPin.spkiPin(leaf.publicKey.encoded)
                Result.Ok(pin = pin, subject = leaf.subjectX500Principal.name)
            }
        } catch (t: Throwable) {
            Result.Failed(t.javaClass.simpleName)
        } finally {
            // Release the probe client's pool/threads promptly; it is single-use.
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    /** True when [baseUrl] is a pinnable (https) endpoint; drives whether the capture button is shown. */
    fun isPinnable(baseUrl: String): Boolean =
        baseUrl.trim().startsWith("https://", ignoreCase = true) &&
            CleartextPolicy.hostOf(baseUrl).isNotEmpty()
}
