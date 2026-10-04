package com.a9ito.hermesagent.core

/**
 * Pure, Android-free policy for deciding when a connection is a cleartext-to-an
 * untrusted-host risk. The bearer token guards an endpoint that can run terminal
 * commands, so sending it over plain HTTP to anything other than a loopback or
 * private-LAN host must be surfaced to the user before it is saved.
 *
 * Android's network-security-config cannot express RFC1918 CIDR ranges
 * declaratively (it matches hostnames/subdomains, not IP ranges), and users
 * legitimately point this client at a LAN IP typed by hand — so cleartext stays
 * permitted at the transport layer, and THIS classifier provides the app-level
 * guard: a save targeting cleartext + a non-local host triggers an explicit
 * confirmation. Loopback and private-LAN cleartext save silently.
 *
 * Kept dependency-free so the host classification is unit-tested on the JVM.
 */
object CleartextPolicy {

    /** True when [baseUrl] uses the plain `http://` scheme (no transport TLS). */
    fun isCleartext(baseUrl: String): Boolean =
        baseUrl.trim().startsWith("http://", ignoreCase = true)

    /**
     * True when [host] is a loopback or private/local address where cleartext is
     * acceptable without a warning: IPv4 loopback (127/8), IPv6 loopback (::1),
     * `localhost`, RFC1918 private IPv4 (10/8, 172.16/12, 192.168/16), link-local
     * (169.254/16, fe80::/10), IPv6 unique-local (fc00::/7), the Android emulator
     * host alias (10.0.2.2 falls under 10/8), and the common private DNS suffixes
     * (`.local`, `.lan`, `.home.arpa`, `.internal`).
     */
    fun isLocalHost(host: String): Boolean {
        val h = host.trim().trim('[', ']').lowercase().substringBefore('%') // strip IPv6 zone id
        if (h.isEmpty()) return false
        if (h == "localhost") return true

        // IPv6
        if (h == "::1") return true
        if (h.startsWith("fe80:")) return true // link-local
        if (h.startsWith("fc") || h.startsWith("fd")) return true // unique-local fc00::/7

        // IPv4
        val octets = h.split(".")
        if (octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }) {
            val a = octets[0].toInt()
            val b = octets[1].toInt()
            return when {
                a == 127 -> true                 // 127.0.0.0/8 loopback
                a == 10 -> true                   // 10.0.0.0/8
                a == 192 && b == 168 -> true      // 192.168.0.0/16
                a == 172 && b in 16..31 -> true   // 172.16.0.0/12
                a == 169 && b == 254 -> true      // 169.254.0.0/16 link-local
                else -> false
            }
        }

        // Hostnames with a private/mDNS suffix.
        return h.endsWith(".local") || h.endsWith(".lan") ||
            h.endsWith(".home.arpa") || h.endsWith(".internal")
    }

    /**
     * Extract the host from a normalized base URL (scheme://authority/path/).
     * Returns the bracket-stripped host without port, or "" if unparseable.
     */
    fun hostOf(baseUrl: String): String {
        val sep = baseUrl.indexOf("://")
        val afterScheme = if (sep == -1) baseUrl else baseUrl.substring(sep + 3)
        val authority = afterScheme.substringBefore('/').substringBefore('?')
        if (authority.startsWith("[")) {
            // [IPv6](:port) — take what's inside the brackets.
            return authority.substringAfter('[').substringBefore(']')
        }
        return authority.substringBefore(':')
    }

    /**
     * True when saving [baseUrl] should prompt the user: cleartext scheme AND a
     * non-local host (public DNS name or public IP). HTTPS is always fine; local
     * cleartext is fine; only plain HTTP to the open internet warns.
     */
    fun requiresCleartextConfirmation(baseUrl: String): Boolean {
        val url = baseUrl.trim()
        if (url.isEmpty() || !isCleartext(url)) return false
        return !isLocalHost(hostOf(url))
    }
}
