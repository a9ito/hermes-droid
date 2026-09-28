package com.a9ito.hermesagent.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTOs for the /api/jobs (cron) resource. Field set + names confirmed against
 * cron/jobs.py::create_job / _normalize_job_record and api_server.py's job
 * handlers. Everything is nullable / defaulted because the server emits only
 * present keys and hand-edited/legacy job records omit some.
 *
 * ``schedule`` itself is a nested object whose shape varies by kind
 * (once/cron/interval), so it is kept as a raw [JsonElement]; the human-readable
 * ``schedule_display`` is what the UI shows.
 */
@Serializable
data class CronJobDto(
    val id: String,
    val name: String? = null,
    val prompt: String? = null,
    val schedule: JsonElement? = null,
    @SerialName("schedule_display") val scheduleDisplay: String? = null,
    val enabled: Boolean = true,
    /** "scheduled" | "paused" | "completed" | "error". */
    val state: String? = null,
    val deliver: String? = null,
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("last_run_at") val lastRunAt: String? = null,
    @SerialName("last_status") val lastStatus: String? = null,
    @SerialName("last_error") val lastError: String? = null,
    val repeat: RepeatDto? = null,
    val skills: List<String> = emptyList(),
)

@Serializable
data class RepeatDto(
    val times: Int? = null,   // null = forever
    val completed: Int = 0,
)

/** GET /api/jobs -> {"jobs": [...]}. */
@Serializable
data class JobListResponse(
    val jobs: List<CronJobDto> = emptyList(),
)

/** create / get / update / pause / resume / run -> {"job": {...}}. */
@Serializable
data class JobEnvelope(
    val job: CronJobDto? = null,
)

/** POST /api/jobs — create a cron job. name + schedule required by the server. */
@Serializable
data class CreateJobRequest(
    val name: String,
    val schedule: String,
    val prompt: String = "",
    val deliver: String? = null,
)

/** PATCH /api/jobs/{id} — only whitelisted fields; all optional. */
@Serializable
data class UpdateJobRequest(
    val name: String? = null,
    val schedule: String? = null,
    val prompt: String? = null,
    val enabled: Boolean? = null,
)
