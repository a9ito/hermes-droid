package com.a9ito.hermesagent.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Streams POST /api/sessions/{id}/chat/stream and turns the Server-Sent Events
 * into a flow of [SessionStreamEvent]. Unlike the stateless [ChatStreamer], this
 * targets a persisted session: the turn is stored server-side, so history
 * survives across app restarts and is shared with every other Hermes surface.
 *
 * The SSE decoding lives in the pure [SessionSseParser] (unit-tested); this
 * class only owns the OkHttp call and line pump. Runs on [Dispatchers.IO] via
 * flowOn — the OkHttp read is blocking and the caller collects on the main
 * dispatcher, so without this Android would throw NetworkOnMainThreadException
 * (the same bug fixed for the legacy chat path).
 *
 * The Bearer header comes from the shared [client]'s [AuthInterceptor]; no token
 * is handled or logged here.
 */
class SessionChatStreamer(
    private val client: OkHttpClient,
    private val json: Json,
) {
    fun stream(baseUrl: String, sessionId: String, requestJson: String): Flow<SessionStreamEvent> = flow {
        val url = baseUrl.trimEnd('/') + "/api/sessions/" + sessionId + "/chat/stream"
        val request = Request.Builder()
            .url(url)
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "text/event-stream")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code)
            }
            val source = response.body?.source() ?: return@flow
            val parser = SessionSseParser(json)
            while (true) {
                val line = SseLineReader.readLine(source) ?: break
                val event = parser.onLine(line) ?: continue
                emit(event)
                if (event is SessionStreamEvent.Done) break
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
