package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlNormalizerTest {

    private fun ok(host: String, port: String?): String? =
        (UrlNormalizer.normalize(host, port) as? UrlNormalizer.Result.Ok)?.baseUrl

    @Test fun ipWithPort() {
        assertEquals("http://192.168.1.10:8642/", ok("192.168.1.10", "8642"))
    }

    @Test fun httpsUrlNoPort() {
        assertEquals("https://my-host/", ok("https://my-host", ""))
    }

    @Test fun portInHostWinsOverField() {
        assertEquals("http://host:9000/", ok("http://host:9000", "8642"))
    }

    @Test fun addsHttpSchemeWhenMissing() {
        assertEquals("http://example.com:8642/", ok("example.com", "8642"))
    }

    @Test fun preservesPathAndAddsTrailingSlash() {
        assertEquals("http://host:8642/base/", ok("host/base", "8642"))
    }

    @Test fun schemeLowercasedHostCasePreserved() {
        assertEquals("http://Host:80/", ok("HTTP://Host", "80"))
    }

    @Test fun blankHostIsEmptyHost() {
        assertTrue(UrlNormalizer.normalize("  ", "8642") is UrlNormalizer.Result.EmptyHost)
    }

    @Test fun outOfRangePortIsInvalid() {
        assertTrue(UrlNormalizer.normalize("host", "70000") is UrlNormalizer.Result.InvalidPort)
        assertTrue(UrlNormalizer.normalize("host", "0") is UrlNormalizer.Result.InvalidPort)
        assertTrue(UrlNormalizer.normalize("host", "-1") is UrlNormalizer.Result.InvalidPort)
    }

    @Test fun nonHttpSchemeIsInvalidHost() {
        assertTrue(UrlNormalizer.normalize("ftp://x", "") is UrlNormalizer.Result.InvalidHost)
    }

    @Test fun nullPortAccepted() {
        assertEquals("https://h/", ok("https://h", null))
    }

    @Test fun connectionConfigCompleteness() {
        assertTrue(ConnectionConfig("http://h/", "tok").isComplete)
        assertFalse(ConnectionConfig("http://h/", "").isComplete)
        assertFalse(ConnectionConfig("", "tok").isComplete)
    }

    @Test fun connectionConfigNeverLeaksTokenInToString() {
        val rendered = ConnectionConfig("http://h/", "s3cr3t-value").toString()
        assertFalse(rendered.contains("s3cr3t-value"))
        assertTrue(rendered.contains("redacted"))
    }
}
