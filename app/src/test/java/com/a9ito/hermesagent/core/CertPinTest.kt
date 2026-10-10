package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CertPin parses user-entered SPKI SHA-256 pins. The security contract: blank
 * means no pinning (default TLS, never weaker), a valid set is de-duplicated and
 * order-preserved, and anything malformed is rejected as a whole (so a typo
 * cannot silently drop a pin and leave the user thinking they are protected).
 */
class CertPinTest {

    // A real sha256/ pin: base64 of 32 zero bytes = 43 chars + '='.
    private val PIN_A = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val PIN_B = "sha256/47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="

    @Test fun blankIsEmpty() {
        assertTrue(CertPin.parse(null) is CertPin.Result.Empty)
        assertTrue(CertPin.parse("") is CertPin.Result.Empty)
        assertTrue(CertPin.parse("   \n  ") is CertPin.Result.Empty)
    }

    @Test fun singleValidPin() {
        val r = CertPin.parse(PIN_A) as CertPin.Result.Ok
        assertEquals(listOf(PIN_A), r.pins)
    }

    @Test fun multiplePinsAcrossSeparators() {
        val r = CertPin.parse("$PIN_A\n$PIN_B , $PIN_A") as CertPin.Result.Ok
        // De-duplicated, order preserved (A then B; the second A is dropped).
        assertEquals(listOf(PIN_A, PIN_B), r.pins)
    }

    @Test fun wrongPrefixRejected() {
        assertTrue(CertPin.parse("sha1/AAAAAAAAAAAAAAAAAAAAAAAAAAA=") is CertPin.Result.Invalid)
        val bad = CertPin.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") as CertPin.Result.Invalid
        assertFalse(bad.bad.startsWith("sha256/"))
    }

    @Test fun wrongLengthDigestRejected() {
        // base64 of 16 bytes, valid base64 but not a 32-byte SHA-256 digest.
        assertTrue(CertPin.parse("sha256/AAAAAAAAAAAAAAAAAAAAAA==") is CertPin.Result.Invalid)
    }

    @Test fun oneBadTokenRejectsTheWholeInput() {
        // A valid pin plus a garbage token must NOT silently keep only the good one.
        val r = CertPin.parse("$PIN_A\nnot-a-pin")
        assertTrue(r is CertPin.Result.Invalid)
        assertEquals("not-a-pin", (r as CertPin.Result.Invalid).bad)
    }

    @Test fun isValidPinChecksShapeAndDigestSize() {
        assertTrue(CertPin.isValidPin(PIN_A))
        assertFalse(CertPin.isValidPin("sha256/short"))
        assertFalse(CertPin.isValidPin("sha256/!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!="))
    }

    @Test fun pinsOrEmptyNeverThrows() {
        assertEquals(emptyList<String>(), CertPin.pinsOrEmpty("garbage"))
        assertEquals(emptyList<String>(), CertPin.pinsOrEmpty(null))
        assertEquals(listOf(PIN_A), CertPin.pinsOrEmpty(PIN_A))
    }

    @Test fun spkiPinMatchesKnownVector() {
        // SHA-256 of 32 zero bytes, base64-encoded, with the sha256/ scheme —
        // this is exactly what OkHttp's CertificatePinner.pin(cert) produces for a
        // key whose SubjectPublicKeyInfo DER is 32 zero bytes, so a captured pin
        // and an enforced pin agree. Vector cross-checked out of band.
        assertEquals(
            "sha256/Zmh6rfhivXdsj8GLjp+OIAiXFIVu4jOzkCpZHQ1fKSU=",
            CertPin.spkiPin(ByteArray(32)),
        )
    }

    @Test fun spkiPinIsAlwaysAValidParseablePin() {
        // Whatever bytes a real certificate yields, the computed pin must satisfy
        // the same validator the save path uses — otherwise a captured pin could
        // be rejected on save.
        val pin = CertPin.spkiPin(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        assertTrue(CertPin.isValidPin(pin))
        assertTrue(CertPin.parse(pin) is CertPin.Result.Ok)
    }
}
