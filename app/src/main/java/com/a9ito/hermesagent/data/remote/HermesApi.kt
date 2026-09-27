package com.a9ito.hermesagent.data.remote

import com.a9ito.hermesagent.data.remote.dto.ChatCompletionRequest
import com.a9ito.hermesagent.data.remote.dto.ChatCompletionResponse
import com.a9ito.hermesagent.data.remote.dto.HealthDetailedDto
import com.a9ito.hermesagent.data.remote.dto.ModelsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit surface for the Hermes API server. Only the endpoints the three
 * screens need, confirmed against gateway/platforms/api_server.py:
 *
 *  - GET  /health/detailed      (Bearer)  -> Status screen
 *  - GET  /v1/models            (Bearer)  -> model id for Status/Chat
 *  - POST /v1/chat/completions  (Bearer)  -> Chat screen (non-streaming)
 *
 * The Bearer header is injected by [AuthInterceptor], not declared per-method,
 * so the token is set in exactly one place and never appears in a log/@Header.
 * Streaming chat (stream=true, SSE) is handled outside Retrofit by
 * [ChatStreamer] because it reads the response body incrementally.
 */
interface HermesApi {

    @GET("health/detailed")
    suspend fun healthDetailed(): HealthDetailedDto

    @GET("v1/models")
    suspend fun models(): ModelsResponse

    @POST("v1/chat/completions")
    suspend fun chatCompletion(@Body request: ChatCompletionRequest): ChatCompletionResponse
}
