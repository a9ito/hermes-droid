package com.a9ito.hermesagent.data.remote

import com.a9ito.hermesagent.data.remote.dto.ChatCompletionChunk
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
 * Streams POST /v1/chat/completions with `stream:true` and parses the
 * Server-Sent Events into incremental text deltas.
 *
 * Hermes' SSE contract (from the API Server docs): each event is a
 * `data: {json}` line carrying a `chat.completion.chunk`; the stream ends with
 * `data: [DONE]`; keep-alive lines begin with `:` and must be skipped; the
 * custom `event: hermes.tool.progress` frames are ignored here (we only render
 * assistant text). Reasoning arrives on `delta.reasoning_content`, which we
 * intentionally skip so private reasoning is not shown.
 *
 * The Bearer header comes from the shared [client]'s [AuthInterceptor]; no token
 * is handled or logged in this file. Emits each content delta as it arrives; the
 * caller accumulates them into the visible message.
 */
class ChatStreamer(
    private val client: OkHttpClient,
    private val json: Json,
) {
    fun stream(baseUrl: String, requestJson: String): Flow<String> = flow {
        val url = baseUrl.trimEnd('/') + "/v1/chat/completions"
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
            while (true) {
                val line = SseLineReader.readLine(source) ?: break
                when {
                    line.isEmpty() -> continue          // event boundary
                    line.startsWith(":") -> continue    // keep-alive comment
                    line.startsWith("data:") -> {
                        val payload = line.substring(5).trim()
                        if (payload == "[DONE]") break
                        val delta = parseDelta(payload) ?: continue
                        if (delta.isNotEmpty()) emit(delta)
                    }
                    // any other line (e.g. "event: ...") is ignored
                }
            }
        }
    }
        // OkHttp execute()/socket reads block the calling thread; viewModelScope
        // collects this flow on Dispatchers.Main, so the blocking upstream must
        // run on the IO dispatcher or Android throws NetworkOnMainThreadException.
        .flowOn(Dispatchers.IO)

    /** Pull `choices[0].delta.content` out of one chunk, tolerating junk. */
    private fun parseDelta(payload: String): String? = try {
        json.decodeFromString(ChatCompletionChunk.serializer(), payload)
            .choices.firstOrNull()?.delta?.content
    } catch (_: Exception) {
        null
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** Non-2xx status from the streaming call; classified by [ErrorMapper] semantics. */
class HttpStatusException(val code: Int) : Exception("HTTP $code")
