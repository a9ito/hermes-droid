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
