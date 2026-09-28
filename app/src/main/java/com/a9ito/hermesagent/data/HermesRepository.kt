package com.a9ito.hermesagent.data

import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.CronJob
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.core.InstanceStatus
import com.a9ito.hermesagent.core.SessionMessage
import com.a9ito.hermesagent.core.SessionSummary
import com.a9ito.hermesagent.data.remote.AuthInterceptor
import com.a9ito.hermesagent.data.remote.ChatStreamer
import com.a9ito.hermesagent.data.remote.ErrorMapper
import com.a9ito.hermesagent.data.remote.HermesApi
import com.a9ito.hermesagent.data.remote.SessionChatStreamer
import com.a9ito.hermesagent.data.remote.SessionStreamEvent
import com.a9ito.hermesagent.data.remote.HttpStatusException
import com.a9ito.hermesagent.data.remote.dto.ChatCompletionRequest
import com.a9ito.hermesagent.data.remote.dto.ChatMessageDto
import com.a9ito.hermesagent.data.remote.dto.CreateJobRequest
import com.a9ito.hermesagent.data.remote.dto.CreateSessionRequest
import com.a9ito.hermesagent.data.remote.dto.ForkSessionRequest
import com.a9ito.hermesagent.data.remote.dto.HealthDetailedDto
import com.a9ito.hermesagent.data.remote.dto.ModelLockRequest
import com.a9ito.hermesagent.data.remote.dto.PatchSessionRequest
import com.a9ito.hermesagent.data.remote.dto.SessionChatRequest
import com.a9ito.hermesagent.data.remote.dto.SkillDto
import com.a9ito.hermesagent.data.remote.dto.ToolsetDto
import com.a9ito.hermesagent.data.remote.dto.toDisplayMessages
import com.a9ito.hermesagent.data.remote.dto.toDomain
import com.a9ito.hermesagent.data.remote.dto.toDomainJobs
import com.a9ito.hermesagent.data.remote.dto.toSummary
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
    private val sessionStreamer = SessionChatStreamer(okHttpClient, json)

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

    /** Available model ids from /v1/models (primary + configured route aliases). */
    suspend fun fetchModels(config: ConnectionConfig): ApiResult<List<String>> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            ApiResult.Success(apiFor(config).models().data.map { it.id })
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /** One session's current metadata (used to show its locked model). */
    suspend fun sessionDetail(config: ConnectionConfig, id: String): ApiResult<SessionSummary> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val env = apiFor(config).getSession(id)
            val session = env.session ?: return ApiResult.Failure(ErrorKind.UNEXPECTED)
            ApiResult.Success(session.toSummary())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    // ---- Skills & Toolsets (read-only viewers) ----

    suspend fun fetchSkills(config: ConnectionConfig): ApiResult<List<SkillDto>> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            ApiResult.Success(apiFor(config).skills().data)
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    suspend fun fetchToolsets(config: ConnectionConfig): ApiResult<List<ToolsetDto>> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            ApiResult.Success(apiFor(config).toolsets().data)
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    // ---- Sessions ----

    suspend fun listSessions(config: ConnectionConfig): ApiResult<List<SessionSummary>> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val rows = apiFor(config).listSessions().data
                .filter { !it.archived }
                .map { it.toSummary() }
            ApiResult.Success(rows)
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    suspend fun createSession(config: ConnectionConfig, title: String?): ApiResult<SessionSummary> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val env = apiFor(config).createSession(CreateSessionRequest(title = title?.takeIf { it.isNotBlank() }))
            val session = env.session ?: return ApiResult.Failure(ErrorKind.UNEXPECTED)
            ApiResult.Success(session.toSummary())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    suspend fun deleteSession(config: ConnectionConfig, id: String): ApiResult<Boolean> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            ApiResult.Success(apiFor(config).deleteSession(id).deleted)
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    suspend fun forkSession(config: ConnectionConfig, id: String): ApiResult<SessionSummary> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val env = apiFor(config).forkSession(id, ForkSessionRequest())
            val session = env.session ?: return ApiResult.Failure(ErrorKind.UNEXPECTED)
            ApiResult.Success(session.toSummary())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    suspend fun renameSession(config: ConnectionConfig, id: String, title: String): ApiResult<SessionSummary> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val env = apiFor(config).patchSession(id, PatchSessionRequest(title = title))
            val session = env.session ?: return ApiResult.Failure(ErrorKind.UNEXPECTED)
            ApiResult.Success(session.toSummary())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    suspend fun sessionMessages(config: ConnectionConfig, id: String): ApiResult<List<SessionMessage>> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            ApiResult.Success(apiFor(config).sessionMessages(id).data.toDisplayMessages())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /** Lock a session to a specific model for subsequent turns. */
    suspend fun lockSessionModel(config: ConnectionConfig, id: String, model: String): ApiResult<Unit> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            apiFor(config).lockSessionModel(id, ModelLockRequest(model = model))
            ApiResult.Success(Unit)
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /**
     * Stream a turn against a persisted session. Emits [SessionStreamEvent]s; the
     * caller renders deltas and the terminal completed/error frame. The turn is
     * stored server-side, so history persists and is shared with other surfaces.
     */
    fun streamSessionChat(
        config: ConnectionConfig,
        sessionId: String,
        message: String,
    ): Flow<SessionStreamEvent> = flow {
        require(config.isComplete)
        tokenRef.set(config.token)
        val body = SessionChatRequest(message = message)
        val payload = json.encodeToString(SessionChatRequest.serializer(), body)
        sessionStreamer.stream(config.baseUrl, sessionId, payload).collect { emit(it) }
    }

    // ---- Cron jobs ----

    suspend fun listJobs(config: ConnectionConfig): ApiResult<List<CronJob>> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            ApiResult.Success(apiFor(config).listJobs().jobs.toDomainJobs())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    suspend fun createJob(
        config: ConnectionConfig, name: String, schedule: String, prompt: String,
    ): ApiResult<CronJob> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val env = apiFor(config).createJob(
                CreateJobRequest(name = name.trim(), schedule = schedule.trim(), prompt = prompt.trim()))
            val job = env.job ?: return ApiResult.Failure(ErrorKind.UNEXPECTED)
            ApiResult.Success(job.toDomain())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    suspend fun deleteJob(config: ConnectionConfig, id: String): ApiResult<Unit> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val resp = apiFor(config).deleteJob(id)
            if (resp.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure(ErrorMapper.classify(HttpStatusException(resp.code())))
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /** Pause, resume, or run-now. Which verb is chosen by the caller via [action]. */
    suspend fun jobAction(config: ConnectionConfig, id: String, action: JobAction): ApiResult<CronJob> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val api = apiFor(config)
            val env = when (action) {
                JobAction.PAUSE -> api.pauseJob(id)
                JobAction.RESUME -> api.resumeJob(id)
                JobAction.RUN -> api.runJob(id)
            }
            val job = env.job ?: return ApiResult.Failure(ErrorKind.UNEXPECTED)
            ApiResult.Success(job.toDomain())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }
}

/** The three side-effecting job verbs the app exposes. */
enum class JobAction { PAUSE, RESUME, RUN }

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
