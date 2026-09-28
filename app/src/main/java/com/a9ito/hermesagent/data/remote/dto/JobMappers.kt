package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.CronJob

/**
 * Pure DTO -> domain mappers for cron jobs. No Android/coroutine types, so they
 * are unit-tested directly on the JVM (JobMapperTest).
 */

private fun stateOf(dto: CronJobDto): CronJob.State {
    // Prefer the explicit state; fall back to enabled so a legacy record without
    // one still lands somewhere sensible rather than UNKNOWN.
    return when (dto.state?.lowercase()) {
        "scheduled" -> CronJob.State.SCHEDULED
        "paused" -> CronJob.State.PAUSED
        "completed" -> CronJob.State.COMPLETED
        "error" -> CronJob.State.ERROR
        null, "" -> if (dto.enabled) CronJob.State.SCHEDULED else CronJob.State.PAUSED
        else -> CronJob.State.UNKNOWN
    }
}

fun CronJobDto.toDomain(): CronJob = CronJob(
    id = id,
    // A job may have no name; fall back to a short id so the row is never blank.
    name = name?.takeIf { it.isNotBlank() } ?: "(job ${id.take(8)})",
    scheduleDisplay = scheduleDisplay?.takeIf { it.isNotBlank() } ?: "?",
    state = stateOf(this),
    enabled = enabled,
    prompt = prompt?.takeIf { it.isNotBlank() },
    deliver = deliver?.takeIf { it.isNotBlank() },
    nextRunAt = nextRunAt,
    lastRunAt = lastRunAt,
    lastStatus = lastStatus,
    lastError = lastError?.takeIf { it.isNotBlank() },
    repeatTimes = repeat?.times,
    repeatCompleted = repeat?.completed ?: 0,
)

fun List<CronJobDto>.toDomainJobs(): List<CronJob> = map { it.toDomain() }
