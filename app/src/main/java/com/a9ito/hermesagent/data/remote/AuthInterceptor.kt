package com.a9ito.hermesagent.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every request using a token supplied
 * lazily by [tokenProvider], so the header tracks the current saved connection
 * without rebuilding the client.
 *
 * The token is only ever placed into the outgoing header. It is never logged,
 * never added to any error message, and no request/response logging interceptor
 * is installed anywhere in this app.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token.isNotBlank()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
