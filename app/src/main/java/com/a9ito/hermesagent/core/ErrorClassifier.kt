package com.a9ito.hermesagent.core

/**
 * Pure HTTP-status → [ErrorKind] classification, with no Retrofit/OkHttp types,
 * so the mapping is unit-testable on the JVM. [ErrorMapper] (which must import
 * Retrofit exception types to dispatch on them) delegates its status handling
 * here.
 *
 * The distinction that matters for the UI: a 5xx is the *instance's* fault
 * (a handler blew up server-side), not the app's or the user's — surfacing it
 * as SERVER_ERROR lets the app say so instead of a generic "unexpected error".
 */
object ErrorClassifier {
    fun fromHttpStatus(code: Int): ErrorKind = when {
        code == 401 || code == 403 -> ErrorKind.AUTH
        code in 500..599 -> ErrorKind.SERVER_ERROR
        else -> ErrorKind.UNEXPECTED
    }
}
