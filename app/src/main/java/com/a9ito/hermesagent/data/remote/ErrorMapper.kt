package com.a9ito.hermesagent.data.remote

import com.a9ito.hermesagent.core.ErrorClassifier
import com.a9ito.hermesagent.core.ErrorKind
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Maps a low-level failure to a coarse [ErrorKind]. Kept tiny and deterministic;
 * no exception detail that could contain the token is ever surfaced (the token
 * only rides in a request header, never in these types). Status-code handling
 * lives in the pure [ErrorClassifier] so it can be unit-tested without Retrofit.
 */
object ErrorMapper {
    fun classify(t: Throwable): ErrorKind = when (t) {
        is SocketTimeoutException -> ErrorKind.TIMEOUT
        is HttpException -> ErrorClassifier.fromHttpStatus(t.code())
        is HttpStatusException -> ErrorClassifier.fromHttpStatus(t.code)
        is IOException -> ErrorKind.NETWORK
        else -> ErrorKind.UNEXPECTED
    }
}
