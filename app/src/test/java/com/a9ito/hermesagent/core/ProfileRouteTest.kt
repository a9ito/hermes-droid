package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRouteTest {

    // -- normalize -------------------------------------------------------------

    @Test fun blankAndDefaultNormalizeToDefault() {
        assertEquals(ProfileRoute.DEFAULT, ProfileRoute.normalize(null))
        assertEquals(ProfileRoute.DEFAULT, ProfileRoute.normalize(""))
        assertEquals(ProfileRoute.DEFAULT, ProfileRoute.normalize("   "))
        assertEquals(ProfileRoute.DEFAULT, ProfileRoute.normalize("default"))
        assertEquals(ProfileRoute.DEFAULT, ProfileRoute.normalize("DEFAULT"))
        assertEquals(ProfileRoute.DEFAULT, ProfileRoute.normalize("  Default  "))
    }

    @Test fun validSecondaryNamesAreTrimmedAndKept() {
        assertEquals("work", ProfileRoute.normalize("  work  "))
        assertEquals("team-a", ProfileRoute.normalize("team-a"))
        assertEquals("proj_1.2", ProfileRoute.normalize("proj_1.2"))
    }

    @Test fun invalidCharactersReturnNull() {
        assertNull(ProfileRoute.normalize("has space"))
        assertNull(ProfileRoute.normalize("slash/name"))
        assertNull(ProfileRoute.normalize("dot.dot/../etc"))
        assertNull(ProfileRoute.normalize("ampersand&"))
    }

    @Test fun dotSegmentsRejected() {
        // "." and ".." pass the charset but an HTTP stack collapses them in the
        // /p/<profile>/ path, silently routing to a different profile. Reject.
        assertNull(ProfileRoute.normalize("."))
        assertNull(ProfileRoute.normalize(".."))
        assertNull(ProfileRoute.normalize("  ..  "))
        // A dotted name that is NOT a pure dot-segment stays valid.
        assertEquals("v1.2", ProfileRoute.normalize("v1.2"))
        assertEquals("...a", ProfileRoute.normalize("...a"))
    }

    // -- isSecondary -----------------------------------------------------------

    @Test fun isSecondaryOnlyForRealProfiles() {
        assertFalse(ProfileRoute.isSecondary(null))
        assertFalse(ProfileRoute.isSecondary(""))
        assertFalse(ProfileRoute.isSecondary("default"))
        assertFalse(ProfileRoute.isSecondary("bad/name"))
        assertTrue(ProfileRoute.isSecondary("work"))
    }

    // -- effectiveBaseUrl ------------------------------------------------------

    @Test fun defaultProfileLeavesBaseUrlUnchanged() {
        val base = "http://10.0.0.5:8642/"
        assertEquals(base, ProfileRoute.effectiveBaseUrl(base, null))
        assertEquals(base, ProfileRoute.effectiveBaseUrl(base, "default"))
        assertEquals(base, ProfileRoute.effectiveBaseUrl(base, "   "))
    }

    @Test fun secondaryProfilePrefixesPSegmentKeepingTrailingSlash() {
        assertEquals(
            "http://10.0.0.5:8642/p/work/",
            ProfileRoute.effectiveBaseUrl("http://10.0.0.5:8642/", "work"),
        )
    }

    @Test fun secondaryProfileAddsSlashWhenBaseLacksOne() {
        // UrlNormalizer always yields a trailing slash, but guard the join anyway.
        assertEquals(
            "https://host/p/team-a/",
            ProfileRoute.effectiveBaseUrl("https://host", "team-a"),
        )
    }

    @Test fun invalidProfileFallsBackToBareBaseUrl() {
        val base = "http://host:1234/"
        assertEquals(base, ProfileRoute.effectiveBaseUrl(base, "bad/name"))
    }

    @Test fun blankBaseUrlIsReturnedUnchanged() {
        assertEquals("", ProfileRoute.effectiveBaseUrl("", "work"))
    }
}
