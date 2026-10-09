package com.a9ito.hermesagent.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Streams GET /v1/runs/{id}/events and turns the Server-Sent Events into a flow
 * of [RunStreamEvent]. Same shape as [SessionChatStreamer] (blocking OkHttp read
 * pumped on [Dispatchers.IO] via flowOn), but this is a GET subscription to a
 * run's lifecycle events, not a chat POST.
 *
 * SSE decoding lives in the pure [RunSseParser] (unit-tested); this class only
 * owns the OkHttp call and line pump. The Bearer header comes from the shared
 * [client]'s AuthInterceptor; no token is handled or logged here.
 */
class RunEventStreamer(
    private val client: OkHttpClient,
    private val json: Json,
) {
    fun stream(baseUrl: String, runId: String): Flow<RunStreamEvent> = flow {
        val url = baseUrl.trimEnd('/') + "/v1/runs/" + runId + "/events"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/event-stream")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code)
            }
            val source = response.body?.source() ?: return@flow
            val parser = RunSseParser(json)
            while (true) {
                val line = SseLineReader.readLine(source) ?: break
                val event = parser.onLine(line) ?: continue
                emit(event)
                if (event is RunStreamEvent.Done || event is RunStreamEvent.Terminal) break
            }
        }
    }.flowOn(Dispatchers.IO)
}
