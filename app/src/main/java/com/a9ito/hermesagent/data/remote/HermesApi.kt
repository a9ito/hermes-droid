package com.a9ito.hermesagent.data.remote

import com.a9ito.hermesagent.data.remote.dto.ChatCompletionRequest
import com.a9ito.hermesagent.data.remote.dto.ChatCompletionResponse
import com.a9ito.hermesagent.data.remote.dto.CreateJobRequest
import com.a9ito.hermesagent.data.remote.dto.CreateSessionRequest
import com.a9ito.hermesagent.data.remote.dto.DeleteSessionResponse
import com.a9ito.hermesagent.data.remote.dto.ForkSessionRequest
import com.a9ito.hermesagent.data.remote.dto.HealthDetailedDto
import com.a9ito.hermesagent.data.remote.dto.JobEnvelope
import com.a9ito.hermesagent.data.remote.dto.JobListResponse
import com.a9ito.hermesagent.data.remote.dto.ModelLockRequest
import com.a9ito.hermesagent.data.remote.dto.ModelsResponse
import com.a9ito.hermesagent.data.remote.dto.PatchSessionRequest
import com.a9ito.hermesagent.data.remote.dto.SessionEnvelope
import com.a9ito.hermesagent.data.remote.dto.SessionListResponse
import com.a9ito.hermesagent.data.remote.dto.SessionMessagesResponse
import com.a9ito.hermesagent.data.remote.dto.SkillListResponse
import com.a9ito.hermesagent.data.remote.dto.ToolsetListResponse
import com.a9ito.hermesagent.data.remote.dto.UpdateJobRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit surface for the Hermes API server. Endpoints confirmed against
 * gateway/platforms/api_server.py:
 *
 *  Health / inventory (Status + viewers)
 *  - GET    /health/detailed              -> Status screen
 *  - GET    /v1/models                    -> model id for Status/Chat
 *  - GET    /v1/skills                    -> Skills viewer
 *  - GET    /v1/toolsets                  -> Toolsets viewer
 *
 *  Stateless chat (legacy quick chat)
 *  - POST   /v1/chat/completions          -> non-streaming fallback
 *
 *  Persistent sessions (Sessions screen + session-scoped chat)
 *  - GET    /api/sessions                 -> list
 *  - POST   /api/sessions                 -> create
 *  - GET    /api/sessions/{id}            -> detail
 *  - PATCH  /api/sessions/{id}            -> rename / pin / archive
 *  - DELETE /api/sessions/{id}            -> delete
 *  - GET    /api/sessions/{id}/messages   -> transcript
 *  - POST   /api/sessions/{id}/fork       -> branch
 *  - POST   /api/sessions/{id}/model      -> per-session model lock
 *
 * The Bearer header is injected by [AuthInterceptor], not declared per method,
 * so the token is set in exactly one place and never appears in a log/@Header.
 * The session chat STREAM (POST /api/sessions/{id}/chat/stream, SSE) is handled
 * outside Retrofit by [SessionChatStreamer] because it reads the body
 * incrementally.
 */
interface HermesApi {

    @GET("health/detailed")
    suspend fun healthDetailed(): HealthDetailedDto

    @GET("v1/models")
    suspend fun models(): ModelsResponse

    @GET("v1/skills")
    suspend fun skills(): SkillListResponse

    @GET("v1/toolsets")
    suspend fun toolsets(): ToolsetListResponse

    @POST("v1/chat/completions")
    suspend fun chatCompletion(@Body request: ChatCompletionRequest): ChatCompletionResponse

    // ---- Sessions ----

    @GET("api/sessions")
    suspend fun listSessions(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): SessionListResponse

    @POST("api/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): SessionEnvelope

    @GET("api/sessions/{id}")
    suspend fun getSession(@Path("id") id: String): SessionEnvelope

    @PATCH("api/sessions/{id}")
    suspend fun patchSession(@Path("id") id: String, @Body request: PatchSessionRequest): SessionEnvelope

    @DELETE("api/sessions/{id}")
    suspend fun deleteSession(@Path("id") id: String): DeleteSessionResponse

    @GET("api/sessions/{id}/messages")
    suspend fun sessionMessages(
        @Path("id") id: String,
        @Query("order") order: String = "oldest",
    ): SessionMessagesResponse

    @POST("api/sessions/{id}/fork")
    suspend fun forkSession(@Path("id") id: String, @Body request: ForkSessionRequest): SessionEnvelope

    @POST("api/sessions/{id}/model")
    suspend fun lockSessionModel(@Path("id") id: String, @Body request: ModelLockRequest)

    // ---- Cron jobs ----

    @GET("api/jobs")
    suspend fun listJobs(
        @Query("include_disabled") includeDisabled: Boolean = true,
    ): JobListResponse

    @POST("api/jobs")
    suspend fun createJob(@Body request: CreateJobRequest): JobEnvelope

    @GET("api/jobs/{id}")
    suspend fun getJob(@Path("id") id: String): JobEnvelope

    @PATCH("api/jobs/{id}")
    suspend fun updateJob(@Path("id") id: String, @Body request: UpdateJobRequest): JobEnvelope

    @DELETE("api/jobs/{id}")
    suspend fun deleteJob(@Path("id") id: String): retrofit2.Response<Unit>

    @POST("api/jobs/{id}/pause")
    suspend fun pauseJob(@Path("id") id: String): JobEnvelope

    @POST("api/jobs/{id}/resume")
    suspend fun resumeJob(@Path("id") id: String): JobEnvelope

    @POST("api/jobs/{id}/run")
    suspend fun runJob(@Path("id") id: String): JobEnvelope
}
