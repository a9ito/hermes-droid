package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.CronJob
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior contract for cron DTO -> domain mapping: state resolution (explicit
 * vs enabled fallback), name/schedule fallbacks, repeat, and the pause/resume
 * affordance flags the UI gates buttons on.
 */
class JobMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun explicitStateWins() {
        assertEquals(CronJob.State.PAUSED, CronJobDto(id = "a", state = "paused").toDomain().state)
        assertEquals(CronJob.State.SCHEDULED, CronJobDto(id = "a", state = "scheduled").toDomain().state)
        assertEquals(CronJob.State.COMPLETED, CronJobDto(id = "a", state = "completed").toDomain().state)
        assertEquals(CronJob.State.ERROR, CronJobDto(id = "a", state = "error").toDomain().state)
    }

    @Test fun missingStateFallsBackToEnabled() {
        assertEquals(CronJob.State.SCHEDULED, CronJobDto(id = "a", state = null, enabled = true).toDomain().state)
        assertEquals(CronJob.State.PAUSED, CronJobDto(id = "a", state = null, enabled = false).toDomain().state)
    }

    @Test fun unrecognizedStateIsUnknownNotCrash() {
        assertEquals(CronJob.State.UNKNOWN, CronJobDto(id = "a", state = "quantum").toDomain().state)
    }

    @Test fun nameFallsBackToShortId() {
        val job = CronJobDto(id = "abcdef1234567890", name = null).toDomain()
        assertTrue(job.name.contains("abcdef12"))
        assertTrue(job.name.isNotBlank())
    }

    @Test fun scheduleDisplayFallsBackToQuestionMark() {
        assertEquals("?", CronJobDto(id = "a", scheduleDisplay = null).toDomain().scheduleDisplay)
        assertEquals("every 2h", CronJobDto(id = "a", scheduleDisplay = "every 2h").toDomain().scheduleDisplay)
    }

    @Test fun pauseResumeAffordancesMatchState() {
        val scheduled = CronJobDto(id = "a", state = "scheduled").toDomain()
        assertTrue(scheduled.canPause); assertFalse(scheduled.canResume)
        val paused = CronJobDto(id = "a", state = "paused").toDomain()
        assertTrue(paused.canResume); assertFalse(paused.canPause)
        val done = CronJobDto(id = "a", state = "completed").toDomain()
        assertFalse(done.canPause); assertFalse(done.canResume)
    }

    @Test fun repeatMappedWithForeverAsNull() {
        val forever = CronJobDto(id = "a", repeat = RepeatDto(times = null, completed = 3)).toDomain()
        assertNull(forever.repeatTimes)
        assertEquals(3, forever.repeatCompleted)
        val finite = CronJobDto(id = "a", repeat = RepeatDto(times = 5, completed = 2)).toDomain()
        assertEquals(5, finite.repeatTimes)
    }

    @Test fun deserializesRealServerJobShape() {
        // Shape emitted by cron/jobs.py: schedule is a nested object, kept raw.
        val payload = """
            {"jobs":[{"id":"abc123def456","name":"daily brief","prompt":"summarize",
            "schedule":{"kind":"cron","expr":"0 9 * * *","display":"every day 9am"},
            "schedule_display":"every day 9am","enabled":true,"state":"scheduled",
            "deliver":"telegram","next_run_at":"2026-09-29T09:00:00Z","last_run_at":null,
            "repeat":{"times":null,"completed":0}}]}
        """.trimIndent()
        val jobs = json.decodeFromString(JobListResponse.serializer(), payload).jobs.toDomainJobs()
        assertEquals(1, jobs.size)
        val j = jobs.single()
        assertEquals("daily brief", j.name)
        assertEquals("every day 9am", j.scheduleDisplay)
        assertEquals(CronJob.State.SCHEDULED, j.state)
        assertEquals("telegram", j.deliver)
        assertTrue(j.canPause)
    }

    @Test fun toleratesUnknownFieldsFromNewerServer() {
        val payload = """{"jobs":[{"id":"a","name":"x","brand_new_field":42,"workdir":"/tmp"}]}"""
        val jobs = json.decodeFromString(JobListResponse.serializer(), payload).jobs.toDomainJobs()
        assertEquals(1, jobs.size)
        assertEquals("x", jobs.single().name)
    }

    // PATCH /api/jobs/{id} is a partial update: only the fields actually set are
    // sent (explicitNulls=false), so an edit of just name+schedule+prompt never
    // sends enabled=null and can't accidentally pause/resume the job.
    @Test fun updateRequestOmitsUnsetFields() {
        val encoded = partialJson
            .encodeToString(UpdateJobRequest.serializer(), UpdateJobRequest(name = "n", schedule = "every 2h", prompt = "p"))
        assertTrue(encoded.contains(""""name":"n""""))
        assertTrue(encoded.contains(""""schedule":"every 2h""""))
        assertTrue(encoded.contains(""""prompt":"p""""))
        assertFalse(encoded.contains("enabled"))
    }

    @Test fun updateRequestEnabledOnlyOmitsText() {
        val encoded = partialJson
            .encodeToString(UpdateJobRequest.serializer(), UpdateJobRequest(enabled = false))
        assertEquals("""{"enabled":false}""", encoded)
    }

    private companion object {
        // explicitNulls=false matches the repository's encoder, so unset fields are omitted.
        val partialJson = Json { explicitNulls = false }
    }
}
