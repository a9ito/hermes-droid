package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SafeText neutralizes the Trojan-Source class (CWE-451) on security-sensitive
 * control displays while leaving ordinary prose, RTL letters, and emoji intact.
 */
class SafeTextTest {

    @Test fun nullAndEmptyPassThrough() {
        assertNull(SafeText.forControlDisplay(null))
        assertEquals("", SafeText.forControlDisplay(""))
    }

    @Test fun plainAsciiUnchangedAndSameInstance() {
        val s = "rm -rf /tmp/cache"
        // Fast path returns the same instance when nothing needs neutralizing.
        assertSame(s, SafeText.forControlDisplay(s))
    }

    @Test fun rtlLettersAndEmojiAreKept() {
        // Arabic letters (not format chars) and an emoji surrogate pair must survive.
        val s = "عربى 😀 日本語"
        assertSame(s, SafeText.forControlDisplay(s))
    }

    @Test fun bidiOverrideIsNeutralized() {
        // RLO (U+202E) is the classic "display reversed" Trojan-Source char.
        val malicious = "rm \u202Egpj.sh"
        val safe = SafeText.forControlDisplay(malicious)!!
        assertTrue("RLO must be gone", !safe.contains('\u202E'))
        assertTrue("replaced with U+FFFD", safe.contains('\uFFFD'))
        // Visible letters are preserved, only the directional override is replaced.
        assertEquals("rm \uFFFDgpj.sh", safe)
    }

    @Test fun zeroWidthAndBomNeutralized() {
        val s = "ls\u200B\uFEFF -la"           // ZWSP + BOM hiding inside a command
        val safe = SafeText.forControlDisplay(s)!!
        assertTrue(!safe.contains('\u200B') && !safe.contains('\uFEFF'))
        assertEquals("ls\uFFFD\uFFFD -la", safe)
    }

    @Test fun bidiIsolatesNeutralized() {
        // LRI/RLI/FSI/PDI (U+2066..U+2069) are the newer isolate formatters.
        val s = "a\u2066b\u2067c\u2068d\u2069e"
        val safe = SafeText.forControlDisplay(s)!!
        assertEquals("a\uFFFDb\uFFFDc\uFFFDd\uFFFDe", safe)
    }

    @Test fun c0AndC1ControlsNeutralizedButTabNewlineKept() {
        val s = "a\u0000b\u0007c\u009Fd\te\nf\rg"
        val safe = SafeText.forControlDisplay(s)!!
        // NUL, BEL, C1, and CR replaced; TAB and LF kept.
        assertEquals("a\uFFFDb\uFFFDc\uFFFDd\te\nf\uFFFDg", safe)
    }
}
