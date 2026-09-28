package com.a9ito.hermesagent.core

/**
 * Domain view of one cron job, mapped from CronJobDto. Pure / Android-free so it
 * can be built and asserted in host unit tests. [state] is the coarse lifecycle
 * the UI colours on; [scheduleDisplay] is the human-readable schedule.
 */
data class CronJob(
    val id: String,
    val name: String,
    val scheduleDisplay: String,
    val state: State,
    val enabled: Boolean,
    val prompt: String? = null,
    val deliver: String? = null,
    val nextRunAt: String? = null,
    val lastRunAt: String? = null,
    val lastStatus: String? = null,
    val lastError: String? = null,
    /** null = repeats forever; otherwise remaining = times - completed. */
    val repeatTimes: Int? = null,
    val repeatCompleted: Int = 0,
) {
    enum class State { SCHEDULED, PAUSED, COMPLETED, ERROR, UNKNOWN }

    /** A paused job can be resumed; a scheduled one can be paused. Terminal states neither. */
    val canPause: Boolean get() = state == State.SCHEDULED
    val canResume: Boolean get() = state == State.PAUSED
}
