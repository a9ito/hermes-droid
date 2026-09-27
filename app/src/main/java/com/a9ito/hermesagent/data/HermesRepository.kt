package com.a9ito.hermesagent.data

import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.core.InstanceStatus
import com.a9ito.hermesagent.data.remote.AuthInterceptor
import com.a9ito.hermesagent.data.remote.ChatStreamer
import com.a9ito.hermesagent.data.remote.ErrorMapper
import com.a9ito.hermesagent.data.remote.HermesApi
import com.a9ito.hermesagent.data.remote.dto.ChatCompletionRequest
import com.a9ito.hermesagent.data.remote.dto.ChatMessageDto
import com.a9ito.hermesagent.data.remote.dto.HealthDetailedDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Outcome wrapper so ViewModels get either data or a classified error. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val kind: ErrorKind) : ApiResult<Nothing>
}

/**
 * Single networking entry point. Rebuilds the Retrofit/OkHttp stack whenever the
 * saved [ConnectionConfig] changes (new base URL). The Bearer token is read
 * through an [AtomicReference] by [AuthInterceptor], so it is applied to every
 * request from exactly one place and never logged.
 */
class HermesRepository(
    private val settings: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        // Always emit model/stream even when equal to their defaults, so the
        // OpenAI-compatible request is explicit on the wire.
        encodeDefaults = true
    }

    private val tokenRef = AtomicReference("")

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // long tool turns
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor { tokenRef.get() })
        .build()

    private val streamer = ChatStreamer(okHttpClient, json)

    // Cache one Retrofit per base URL so we don't rebuild on every call.
    @Volatile private var cachedBaseUrl: String = ""
    @Volatile private var cachedApi: HermesApi? = null

    val connectionFlow: Flow<ConnectionConfig> = settings.connectionFlow

    private fun apiFor(config: ConnectionConfig): HermesApi {
        tokenRef.set(config.token)
        val existing = cachedApi
        if (existing != null && cachedBaseUrl == config.baseUrl) return existing

        @Suppress("OPT_IN_USAGE")
        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
        val api = retrofit.create(HermesApi::class.java)
        cachedBaseUrl = config.baseUrl
        cachedApi = api
        return api
    }

    /** Combined status from /health/detailed (+ /v1/models for the model id). */
    suspend fun fetchStatus(config: ConnectionConfig): ApiResult<InstanceStatus> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val api = apiFor(config)
            val health = api.healthDetailed()
            val model = runCatching { api.models().primaryModelId() }.getOrNull()
            ApiResult.Success(health.toInstanceStatus(model))
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /** Non-streaming chat turn. [history] is the full prior transcript. */
    suspend fun sendChat(
        config: ConnectionConfig,
        history: List<ChatMessageDto>,
    ): ApiResult<String> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val api = apiFor(config)
            val resp = api.chatCompletion(ChatCompletionRequest(messages = history, stream = false))
            ApiResult.Success(resp.firstText())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /**
     * Streaming chat turn. Emits incremental assistant text deltas. The caller
     * accumulates them; errors propagate as exceptions to be classified.
     */
    fun streamChat(
        config: ConnectionConfig,
        history: List<ChatMessageDto>,
    ): Flow<String> = flow {
        require(config.isComplete)
        tokenRef.set(config.token)
        val body = ChatCompletionRequest(messages = history, stream = true)
        val payload = json.encodeToString(ChatCompletionRequest.serializer(), body)
        streamer.stream(config.baseUrl, payload).collect { emit(it) }
    }

    fun classify(t: Throwable): ErrorKind = ErrorMapper.classify(t)
}

private fun HealthDetailedDto.toInstanceStatus(modelId: String?): InstanceStatus =
    InstanceStatus(
        reachable = true,
        overallStatus = status,
        readiness = readiness?.status,
        gatewayState = gatewayState,
        busy = gatewayBusy,
        activeAgents = activeAgents,
        model = modelId,
        version = version,
        connectedPlatforms = connectedPlatformNames(),
        updatedAt = updatedAt,
    )

/** Extract connected-platform names from the raw platforms map, best-effort. */
private fun HealthDetailedDto.connectedPlatformNames(): List<String> {
    val map = platforms ?: return emptyList()
    return map.keys.filter { key ->
        when (val v = map[key]) {
            is JsonObject -> {
                val connected = (v["connected"] as? JsonPrimitive)?.content
                val state = (v["state"] as? JsonPrimitive)?.content
                connected == "true" || state == "connected" || (connected == null && state == null)
            }
            is JsonPrimitive -> v.content == "connected" || v.content == "true"
            else -> true
        }
    }.sorted()
}
