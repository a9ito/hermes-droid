package com.a9ito.hermesagent.data

import com.a9ito.hermesagent.core.AgentRun
import com.a9ito.hermesagent.core.ChatAttachment
import com.a9ito.hermesagent.core.Capabilities
import com.a9ito.hermesagent.core.CleartextPolicy
import com.a9ito.hermesagent.core.ConnectionConfig
import com.a9ito.hermesagent.core.CronJob
import com.a9ito.hermesagent.core.ErrorKind
import com.a9ito.hermesagent.core.ModelOptions
import com.a9ito.hermesagent.core.InstanceStatus
import com.a9ito.hermesagent.core.SessionMessage
import com.a9ito.hermesagent.core.SessionSummary
import com.a9ito.hermesagent.data.remote.AuthInterceptor
import com.a9ito.hermesagent.data.remote.CertificateProbe
import com.a9ito.hermesagent.data.remote.ChatStreamer
import com.a9ito.hermesagent.data.remote.ErrorMapper
import com.a9ito.hermesagent.data.remote.HermesApi
import com.a9ito.hermesagent.data.remote.RunEventStreamer
import com.a9ito.hermesagent.data.remote.RunStreamEvent
import com.a9ito.hermesagent.data.remote.SessionChatStreamer
import com.a9ito.hermesagent.data.remote.SessionStreamEvent
import com.a9ito.hermesagent.data.remote.HttpStatusException
import com.a9ito.hermesagent.data.remote.dto.ApprovalRequestBody
import com.a9ito.hermesagent.data.remote.dto.ChatCompletionPayload
import com.a9ito.hermesagent.data.remote.dto.ChatMessageDto
import com.a9ito.hermesagent.data.remote.dto.CreateJobRequest
import com.a9ito.hermesagent.data.remote.dto.UpdateJobRequest
import com.a9ito.hermesagent.data.remote.dto.CreateRunRequest
import com.a9ito.hermesagent.data.remote.dto.CreateSessionRequest
import com.a9ito.hermesagent.data.remote.dto.ForkSessionRequest
import com.a9ito.hermesagent.data.remote.dto.HealthDetailedDto
import com.a9ito.hermesagent.data.remote.dto.ModelLockRequest
import com.a9ito.hermesagent.data.remote.dto.PatchSessionRequest
import com.a9ito.hermesagent.data.remote.dto.SessionChatPayload
import com.a9ito.hermesagent.data.remote.dto.SkillDto
import com.a9ito.hermesagent.data.remote.dto.SteerRequest
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
import okhttp3.CertificatePinner
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

    /** One atomically-published networking stack for a given (host, pin-set) key. */
    private class Clients(
        val key: String,
        val base: OkHttpClient,
        val stream: OkHttpClient,
        val api: HermesApi,
    )

    // The base + streaming OkHttp clients are rebuilt only when the set of TLS
    // pins changes (rare), and cached by a pins key, mirroring the Retrofit
    // per-base-URL cache below. A CertificatePinner is immutable once built into
    // a client, and the pins come from the (editable) saved config, so the client
    // cannot be a single constructor-time singleton.
    private fun buildBaseClient(pins: List<String>, host: String): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // long tool turns
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor { tokenRef.get() })
        // Pin only when the user supplied pins AND we have a host to bind them to.
        // CertificatePinner only acts on TLS connections, so a cleartext LAN
        // connection is unaffected; https to a non-matching cert is rejected
        // before the token is sent.
        if (pins.isNotEmpty() && host.isNotEmpty()) {
            val pinnerBuilder = CertificatePinner.Builder()
            for (pin in pins) pinnerBuilder.add(host, pin)
            builder.certificatePinner(pinnerBuilder.build())
        }
        return builder.build()
    }

    // Single atomically-published cache. The whole swap (base + streaming client +
    // Retrofit api + their key) is one object reference, so a reader never pairs a
    // client with a mismatched pin-set/host even if two configs race — the previous
    // triple-@Volatile swap could interleave. @Synchronized serializes the rebuild
    // and lets us evict the superseded connection pool promptly instead of waiting
    // for OkHttp's idle timeout.
    //
    // SSE streams stay open for a whole agent turn, which legitimately runs for many
    // minutes while server-side tools execute; the keepalive can starve past any
    // fixed idle bound, so the streaming client lifts only the idle-read cap
    // (readTimeout(0)) while connect/write stay to detect a dead socket. It reuses
    // the base client's connection pool + AuthInterceptor + certificate pinner via
    // newBuilder().
    @Volatile private var cachedClients: Clients? = null

    @Synchronized
    private fun clientsFor(config: ConnectionConfig): Clients {
        tokenRef.set(config.token)
        val base = config.effectiveBaseUrl
        // Key on the host too: a pin set is bound to a specific host, so a base
        // URL change must rebuild the pinner even if the pin strings are the same.
        val host = CleartextPolicy.hostOf(base)
        val key = host + "|" + config.certPins.joinToString(",") + "|" + base
        cachedClients?.let { if (it.key == key) return it }

        val previous = cachedClients
        val newBase = buildBaseClient(config.certPins, host)
        val newStream = newBase.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()
        @Suppress("OPT_IN_USAGE")
        val contentType = "application/json".toMediaType()
        val api = Retrofit.Builder()
            .baseUrl(base)
            .client(newBase)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(HermesApi::class.java)
        val clients = Clients(key = key, base = newBase, stream = newStream, api = api)
        cachedClients = clients
        // Release the superseded stack's sockets/threads now rather than on idle TTL.
        previous?.base?.connectionPool?.evictAll()
        return clients
    }

    val connectionFlow: Flow<ConnectionConfig> = settings.connectionFlow

    private fun apiFor(config: ConnectionConfig): HermesApi = clientsFor(config).api

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

    /**
     * GET /v1/capabilities. A 404/older gateway (no such endpoint) is NOT an
     * error the user should see — it means "capabilities unknown", so we map it
     * to [Capabilities.baseline] rather than a failure. Any other failure (auth,
     * network) still surfaces so the caller can react.
     */
    suspend fun fetchCapabilities(config: ConnectionConfig): ApiResult<Capabilities> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            ApiResult.Success(apiFor(config).capabilities().toDomain())
        } catch (t: Throwable) {
            val kind = ErrorMapper.classify(t)
            if (t is retrofit2.HttpException && t.code() == 404) {
                ApiResult.Success(Capabilities.baseline())
            } else {
                ApiResult.Failure(kind)
            }
        }
    }

    /** Non-streaming chat turn. [history] is the full prior transcript. */
    suspend fun sendChat(
        config: ConnectionConfig,
        history: List<ChatMessageDto>,
        model: String? = null,
        provider: String? = null,
        attachments: List<ChatAttachment> = emptyList(),
        modelOptions: JsonObject? = null,
    ): ApiResult<String> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val api = apiFor(config)
            val body = ChatCompletionPayload.build(model, history, stream = false, provider = provider, attachments = attachments, modelOptions = modelOptions)
            val resp = api.chatCompletion(body)
            ApiResult.Success(resp.firstText())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /**
     * Streaming chat turn. Emits incremental assistant text deltas. The caller
     * accumulates them; errors propagate as exceptions to be classified.
     * [attachments] attach inline images to the last (current) user turn.
     */
    fun streamChat(
        config: ConnectionConfig,
        history: List<ChatMessageDto>,
        model: String? = null,
        provider: String? = null,
        attachments: List<ChatAttachment> = emptyList(),
        modelOptions: JsonObject? = null,
    ): Flow<String> = flow {
        require(config.isComplete)
        tokenRef.set(config.token)
        val payload = ChatCompletionPayload.encode(json, model, history, stream = true, provider = provider, attachments = attachments, modelOptions = modelOptions)
        val streamingClient = clientsFor(config).stream
        ChatStreamer(streamingClient, json).stream(config.effectiveBaseUrl, payload).collect { emit(it) }
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

    /**
     * Rich provider catalog from /api/model/options (providers, current
     * selection, capability + pricing hints). Only meaningful when the instance
     * advertises the model_options capability; callers gate on that and fall
     * back to [fetchModels] otherwise.
     */
    suspend fun fetchModelOptions(config: ConnectionConfig, refresh: Boolean = false): ApiResult<ModelOptions> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            ApiResult.Success(apiFor(config).modelOptions(refresh).toDomain())
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

    /**
     * List sessions. [includeChildren] also returns forked/child sessions
     * (default on, so a fork the user just made is visible instead of silently
     * absent). Archived rows are dropped client-side.
     */
    suspend fun listSessions(
        config: ConnectionConfig,
        includeChildren: Boolean = true,
    ): ApiResult<List<SessionSummary>> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val rows = apiFor(config).listSessions(includeChildren = includeChildren).data
                .filter { !it.archived }
                .map { it.toSummary() }
            ApiResult.Success(rows)
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /**
     * Create a session. [model] pins the session to a model up front (the native
     * session endpoints honor a bare model, so no provider is needed here, unlike
     * the stateless completions path); [systemPrompt] seeds a custom system prompt.
     * Both are omitted when blank so a plain new session stays {"title": ...}.
     */
    suspend fun createSession(
        config: ConnectionConfig,
        title: String?,
        model: String? = null,
        systemPrompt: String? = null,
    ): ApiResult<SessionSummary> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val env = apiFor(config).createSession(
                CreateSessionRequest(
                    title = title?.takeIf { it.isNotBlank() },
                    model = model?.takeIf { it.isNotBlank() },
                    systemPrompt = systemPrompt?.takeIf { it.isNotBlank() },
                )
            )
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

    /** Pin or unpin a session (floats it to the top of the list on every surface). */
    suspend fun setSessionPinned(config: ConnectionConfig, id: String, pinned: Boolean): ApiResult<SessionSummary> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val env = apiFor(config).patchSession(id, PatchSessionRequest(pinned = pinned))
            val session = env.session ?: return ApiResult.Failure(ErrorKind.UNEXPECTED)
            ApiResult.Success(session.toSummary())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /** Archive or unarchive a session (archived rows are hidden from the default list). */
    suspend fun setSessionArchived(config: ConnectionConfig, id: String, archived: Boolean): ApiResult<SessionSummary> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val env = apiFor(config).patchSession(id, PatchSessionRequest(archived = archived))
            val session = env.session ?: return ApiResult.Failure(ErrorKind.UNEXPECTED)
            ApiResult.Success(session.toSummary())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /**
     * Load a session's transcript. [includeCompacted] also returns turns a
     * context compaction archived (default: live transcript only). Images are
     * always requested as "[image]" placeholders (inline_images=false): the app
     * renders them as "[image]" regardless, so pulling kilobytes beats pulling
     * multi-MB data URIs over the network.
     */
    suspend fun sessionMessages(
        config: ConnectionConfig,
        id: String,
        includeCompacted: Boolean = false,
    ): ApiResult<List<SessionMessage>> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            ApiResult.Success(
                apiFor(config).sessionMessages(id, includeCompacted = includeCompacted, inlineImages = false)
                    .data.toDisplayMessages()
            )
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
        attachments: List<ChatAttachment> = emptyList(),
        modelOptions: JsonObject? = null,
    ): Flow<SessionStreamEvent> = flow {
        require(config.isComplete)
        tokenRef.set(config.token)
        val payload = SessionChatPayload.encode(json, message, attachments, model = null, modelOptions = modelOptions)
        val streamingClient = clientsFor(config).stream
        SessionChatStreamer(streamingClient, json).stream(config.effectiveBaseUrl, sessionId, payload).collect { emit(it) }
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

    /**
     * PATCH /api/jobs/{id}. Only non-null fields are sent (explicitNulls=false),
     * so this is a partial update: pass just the fields the user changed. The
     * server whitelists name/schedule/prompt/enabled (among others) and ignores
     * the rest.
     */
    suspend fun updateJob(
        config: ConnectionConfig,
        id: String,
        name: String? = null,
        schedule: String? = null,
        prompt: String? = null,
        enabled: Boolean? = null,
    ): ApiResult<CronJob> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val env = apiFor(config).updateJob(
                id,
                UpdateJobRequest(
                    name = name?.trim()?.takeIf { it.isNotBlank() },
                    schedule = schedule?.trim()?.takeIf { it.isNotBlank() },
                    prompt = prompt?.trim(),
                    enabled = enabled,
                ),
            )
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

    // ---- Durable agent runs ----

    /** Submit a new run; returns the admitted run (run_id + initial status). */
    suspend fun createRun(
        config: ConnectionConfig, input: String, model: String? = null, sessionId: String? = null,
    ): ApiResult<AgentRun> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        if (input.isBlank()) return ApiResult.Failure(ErrorKind.UNEXPECTED)
        return try {
            val dto = apiFor(config).createRun(
                CreateRunRequest(input = input.trim(), model = model, sessionId = sessionId))
            ApiResult.Success(dto.toDomain())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /** Poll a run's current status. Used to reconcile after the stream ends or reconnects. */
    suspend fun getRun(config: ConnectionConfig, runId: String): ApiResult<AgentRun> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            ApiResult.Success(apiFor(config).getRun(runId).toDomain())
        } catch (t: Throwable) {
            ApiResult.Failure(ErrorMapper.classify(t))
        }
    }

    /** Subscribe to a run's live event stream (GET /v1/runs/{id}/events). */
    fun streamRunEvents(config: ConnectionConfig, runId: String): Flow<RunStreamEvent> = flow {
        require(config.isComplete)
        tokenRef.set(config.token)
        val streamingClient = clientsFor(config).stream
        RunEventStreamer(streamingClient, json).stream(config.effectiveBaseUrl, runId).collect { emit(it) }
    }

    /**
     * Trust-on-first-use capture of the server's current TLS leaf pin, so the
     * user can turn on pinning without hand-computing a hash. Delegates to
     * [CertificateProbe], which opens a normally-validated HTTPS connection (no
     * bearer token sent) and returns the `sha256/<base64>` SPKI pin. A cleartext
     * base URL yields [CertificateProbe.Result.NotHttps].
     */
    suspend fun captureCertPin(config: ConnectionConfig): CertificateProbe.Result =
        CertificateProbe.probe(config.effectiveBaseUrl)

    suspend fun stopRun(config: ConnectionConfig, runId: String): ApiResult<Unit> =
        runControl(config) { apiFor(config).stopRun(runId) }

    suspend fun steerRun(config: ConnectionConfig, runId: String, text: String): ApiResult<Unit> {
        if (text.isBlank()) return ApiResult.Failure(ErrorKind.UNEXPECTED)
        return runControl(config) { apiFor(config).steerRun(runId, SteerRequest(text.trim())) }
    }

    suspend fun approveRun(
        config: ConnectionConfig, runId: String, choice: String, requestId: String? = null,
    ): ApiResult<Unit> = runControl(config) {
        apiFor(config).approveRun(runId, ApprovalRequestBody(choice = choice, requestId = requestId))
    }

    /** Shared body for the fire-and-forget run-control verbs (stop/steer/approval). */
    private suspend inline fun runControl(
        config: ConnectionConfig, call: () -> retrofit2.Response<Unit>,
    ): ApiResult<Unit> {
        if (!config.isComplete) return ApiResult.Failure(ErrorKind.NO_CONNECTION)
        return try {
            val resp = call()
            if (resp.isSuccessful) ApiResult.Success(Unit)
            else ApiResult.Failure(ErrorMapper.classify(HttpStatusException(resp.code())))
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
