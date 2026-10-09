package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CleartextPolicy guards the one credential this app holds: the bearer token
 * for an endpoint that can run terminal commands. The classification decides
 * whether saving a plain-http:// connection warns (public host) or not
 * (loopback / private LAN / link-local / local-suffix names).
 */
class CleartextPolicyTest {

    // ---- isCleartext ----

    @Test fun `plain http is cleartext`() {
        assertTrue(CleartextPolicy.isCleartext("http://example.com/"))
    }

    @Test fun `https is not cleartext`() {
        assertFalse(CleartextPolicy.isCleartext("https://example.com/"))
    }

    @Test fun `scheme is case-insensitive`() {
        assertTrue(CleartextPolicy.isCleartext("HTTP://Example.COM/"))
        assertFalse(CleartextPolicy.isCleartext("HTTPS://example.com/"))
    }

    @Test fun `blank and garbage are not cleartext`() {
        assertFalse(CleartextPolicy.isCleartext(""))
        assertFalse(CleartextPolicy.isCleartext("   "))
        assertFalse(CleartextPolicy.isCleartext("ftp://example.com/"))
    }

    // ---- isLocalHost ----

    @Test fun `loopback addresses are local`() {
        assertTrue(CleartextPolicy.isLocalHost("127.0.0.1"))
        assertTrue(CleartextPolicy.isLocalHost("127.1.2.3")) // whole 127/8
        assertTrue(CleartextPolicy.isLocalHost("::1"))
        assertTrue(CleartextPolicy.isLocalHost("[::1]"))
        assertTrue(CleartextPolicy.isLocalHost("localhost"))
        assertTrue(CleartextPolicy.isLocalHost("LOCALHOST"))
    }

    @Test fun `private LAN ranges are local`() {
        assertTrue(CleartextPolicy.isLocalHost("10.0.0.5"))
        assertTrue(CleartextPolicy.isLocalHost("10.0.2.2")) // emulator host alias
        assertTrue(CleartextPolicy.isLocalHost("192.168.1.10"))
        assertTrue(CleartextPolicy.isLocalHost("172.16.0.1"))
        assertTrue(CleartextPolicy.isLocalHost("172.31.255.254")) // top of 172.16/12
    }

    @Test fun `public and out-of-range private are not local`() {
        assertFalse(CleartextPolicy.isLocalHost("172.32.0.1")) // just past 172.16/12
        assertFalse(CleartextPolicy.isLocalHost("172.15.255.255"))
        assertFalse(CleartextPolicy.isLocalHost("192.169.0.1"))
        assertFalse(CleartextPolicy.isLocalHost("8.8.8.8"))
        assertFalse(CleartextPolicy.isLocalHost("example.com"))
    }

    @Test fun `link-local and unique-local IPv6 are local`() {
        assertTrue(CleartextPolicy.isLocalHost("fe80::1"))
        assertTrue(CleartextPolicy.isLocalHost("fc00::abcd"))
        assertTrue(CleartextPolicy.isLocalHost("fd12:3456::1"))
        assertTrue(CleartextPolicy.isLocalHost("fe80::1%wlan0")) // zone id stripped
    }

    @Test fun `link-local IPv4 is local`() {
        assertTrue(CleartextPolicy.isLocalHost("169.254.4.4"))
    }

    @Test fun `local-suffix hostnames are local`() {
        assertTrue(CleartextPolicy.isLocalHost("myserver.local"))
        assertTrue(CleartextPolicy.isLocalHost("nas.lan"))
        assertTrue(CleartextPolicy.isLocalHost("box.home.arpa"))
        assertTrue(CleartextPolicy.isLocalHost("host.internal"))
    }

    @Test fun `public-looking hostnames with local-suffix substrings are not local`() {
        assertFalse(CleartextPolicy.isLocalHost("local.example.com")) // suffix must be at the end
        assertFalse(CleartextPolicy.isLocalHost("example.com.local.evil"))
    }

    @Test fun `malformed hosts are not local`() {
        assertFalse(CleartextPolicy.isLocalHost(""))
        assertFalse(CleartextPolicy.isLocalHost("999.999.999.999"))
        assertFalse(CleartextPolicy.isLocalHost("256.1.1.1"))
    }

    @Test fun `public hostnames that merely start with fc or fd are not local`() {
        // Regression: the IPv6 ULA prefix check must not swallow public DNS names
        // that happen to begin with fc/fd, or their cleartext warning is skipped.
        assertFalse(CleartextPolicy.isLocalHost("fc2.com"))
        assertFalse(CleartextPolicy.isLocalHost("fcbarcelona.com"))
        assertFalse(CleartextPolicy.isLocalHost("fd.example.net"))
        assertFalse(CleartextPolicy.isLocalHost("fcm.googleapis.com"))
        assertFalse(CleartextPolicy.isLocalHost("fe80.example.com")) // not an IPv6 literal
    }

    @Test fun `http to a public fc-prefixed host still requires confirmation`() {
        assertTrue(CleartextPolicy.requiresCleartextConfirmation("http://fc2.com:8642/"))
        assertTrue(CleartextPolicy.requiresCleartextConfirmation("http://fd.example.net/"))
    }

    @Test fun `real IPv6 ULA and link-local literals stay local`() {
        // The fix must keep genuine fc00::/7 and fe80::/10 literals classified local.
        assertTrue(CleartextPolicy.isLocalHost("fc00::1"))
        assertTrue(CleartextPolicy.isLocalHost("fd12:3456::1"))
        assertTrue(CleartextPolicy.isLocalHost("fe80::1"))
        assertFalse(CleartextPolicy.requiresCleartextConfirmation("http://[fc00::1]:8642/"))
    }

    // ---- hostOf ----

    @Test fun `hostOf extracts host from urls`() {
        assertEquals("example.com", CleartextPolicy.hostOf("http://example.com:8642/"))
        assertEquals("192.168.1.10", CleartextPolicy.hostOf("http://192.168.1.10:8642/api/"))
        assertEquals("::1", CleartextPolicy.hostOf("http://[::1]:8642/"))
        assertEquals("myserver.local", CleartextPolicy.hostOf("http://myserver.local/"))
    }

    @Test fun `hostOf on unparseable input returns empty`() {
        assertEquals("", CleartextPolicy.hostOf(""))
        assertEquals("", CleartextPolicy.hostOf("http://"))
    }

    @Test fun `hostOf strips userinfo and returns the real host`() {
        // Defense in depth: even though UrlNormalizer rejects userinfo before a
        // save, the classifier must resolve the real host (after `@`), never the
        // deceptive part before it.
        assertEquals("8.8.8.8", CleartextPolicy.hostOf("http://localhost:8642@8.8.8.8:9999/"))
        assertEquals("evil.com", CleartextPolicy.hostOf("http://127.0.0.1@evil.com:8642/"))
        assertEquals("::1", CleartextPolicy.hostOf("http://user:pass@[::1]:8642/"))
    }

    @Test fun `userinfo cannot disguise a public host as local`() {
        assertTrue(CleartextPolicy.requiresCleartextConfirmation("http://localhost:8642@8.8.8.8:9999/"))
        assertTrue(CleartextPolicy.requiresCleartextConfirmation("http://127.0.0.1@evil.com:8642/"))
    }

    // ---- requiresCleartextConfirmation (the gate) ----

    @Test fun `http to public host requires confirmation`() {
        assertTrue(CleartextPolicy.requiresCleartextConfirmation("http://api.example.com:8642/"))
        assertTrue(CleartextPolicy.requiresCleartextConfirmation("http://8.8.8.8/"))
    }

    @Test fun `http to loopback or LAN saves silently`() {
        assertFalse(CleartextPolicy.requiresCleartextConfirmation("http://127.0.0.1:8642/"))
        assertFalse(CleartextPolicy.requiresCleartextConfirmation("http://localhost:8642/"))
        assertFalse(CleartextPolicy.requiresCleartextConfirmation("http://192.168.1.10:8642/"))
        assertFalse(CleartextPolicy.requiresCleartextConfirmation("http://10.0.0.5:8642/"))
        assertFalse(CleartextPolicy.requiresCleartextConfirmation("http://[::1]:8642/"))
        assertFalse(CleartextPolicy.requiresCleartextConfirmation("http://nas.lan:8642/"))
    }

    @Test fun `https never requires confirmation`() {
        assertFalse(CleartextPolicy.requiresCleartextConfirmation("https://api.example.com/"))
    }

    @Test fun `blank never requires confirmation`() {
        assertFalse(CleartextPolicy.requiresCleartextConfirmation(""))
        assertFalse(CleartextPolicy.requiresCleartextConfirmation("   "))
    }

    @Test fun `profile-prefixed public http still requires confirmation`() {
        // The profile prefix composes onto the base URL before this check runs
        // on the plain base; the host is what matters, not the path.
        val base = "http://api.example.com:8642/"
        assertTrue(CleartextPolicy.requiresCleartextConfirmation(base))
        val withProfile = ProfileRoute.effectiveBaseUrl(base, "work")
        assertEquals("http://api.example.com:8642/p/work/", withProfile)
        assertTrue(CleartextPolicy.requiresCleartextConfirmation(withProfile))
    }
}
