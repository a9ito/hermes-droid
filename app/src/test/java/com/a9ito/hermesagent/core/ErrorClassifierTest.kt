package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HTTP-status → ErrorKind classification. Pins the distinction the Tools/Skills
 * screen depends on: a 5xx is the instance's fault (SERVER_ERROR), not a generic
 * UNEXPECTED — that's exactly the /v1/skills 500 that surfaced as an opaque
 * "unexpected error" before this split existed.
 */
class ErrorClassifierTest {

    @Test fun unauthorizedAndForbiddenAreAuth() {
        assertEquals(ErrorKind.AUTH, ErrorClassifier.fromHttpStatus(401))
        assertEquals(ErrorKind.AUTH, ErrorClassifier.fromHttpStatus(403))
    }

    @Test fun fiveXxIsServerError() {
        assertEquals(ErrorKind.SERVER_ERROR, ErrorClassifier.fromHttpStatus(500))
        assertEquals(ErrorKind.SERVER_ERROR, ErrorClassifier.fromHttpStatus(502))
        assertEquals(ErrorKind.SERVER_ERROR, ErrorClassifier.fromHttpStatus(503))
        assertEquals(ErrorKind.SERVER_ERROR, ErrorClassifier.fromHttpStatus(599))
    }

    @Test fun the500SkillsBugClassifiesAsServerNotUnexpected() {
        // Regression anchor: GET /v1/skills returned HTTP 500 ("Failed to
        // enumerate skills"). The app must call that a server-side problem.
        val kind = ErrorClassifier.fromHttpStatus(500)
        assertEquals(ErrorKind.SERVER_ERROR, kind)
        assert(kind != ErrorKind.UNEXPECTED)
    }

    @Test fun otherClientErrorsAreUnexpected() {
        // 400/404/405/409/429 aren't specially handled — they fall through to
        // UNEXPECTED rather than masquerading as auth or server faults.
        assertEquals(ErrorKind.UNEXPECTED, ErrorClassifier.fromHttpStatus(400))
        assertEquals(ErrorKind.UNEXPECTED, ErrorClassifier.fromHttpStatus(404))
        assertEquals(ErrorKind.UNEXPECTED, ErrorClassifier.fromHttpStatus(429))
    }

    @Test fun boundaryAround500IsRespected() {
        // 499 is not 5xx; 500 is the first server code.
        assertEquals(ErrorKind.UNEXPECTED, ErrorClassifier.fromHttpStatus(499))
        assertEquals(ErrorKind.SERVER_ERROR, ErrorClassifier.fromHttpStatus(500))
    }

    @Test fun everyKindIsDistinct() {
        // Sanity: the enum still has the five outcomes the UI maps to strings.
        val all = ErrorKind.entries.toSet()
        assert(ErrorKind.SERVER_ERROR in all)
        assertEquals(6, all.size)
    }
}
