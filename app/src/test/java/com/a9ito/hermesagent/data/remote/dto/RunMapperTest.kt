package com.a9ito.hermesagent.data.remote.dto

import com.a9ito.hermesagent.core.AgentRun
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior contract for run DTO -> domain mapping and the AgentRun.Status
 * affordance flags the UI gates controls on (canSteer/canStop/isTerminal).
 */
class RunMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun admissionMapsRunIdAndStatus() {
        val dto = json.decodeFromString(
            RunAdmissionDto.serializer(), """{"run_id":"run_abc","status":"queued","replayed":false}""")
        val run = dto.toDomain()
        assertEquals("run_abc", run.runId)
        assertEquals(AgentRun.Status.QUEUED, run.status)
    }

    @Test fun statusRecordMapsFields() {
        val dto = json.decodeFromString(
            RunStatusDto.serializer(),
            """{"object":"hermes.run","run_id":"run_1","status":"completed","output":"hi",
               "session_id":"s1","model":"hermes-agent","updated_at":123.4,"last_event":"run.completed"}""")
        val run = dto.toDomain()
        assertEquals(AgentRun.Status.COMPLETED, run.status)
        assertEquals("hi", run.output)
        assertEquals("s1", run.sessionId)
        assertEquals("hermes-agent", run.model)
    }

    @Test fun emptyOutputAndErrorBecomeNull() {
        val run = RunStatusDto(runId = "r", status = "running", output = "", error = "").toDomain()
        assertNull(run.output)
        assertNull(run.error)
    }

    @Test fun unknownStatusIsUnknownNotCrash() {
        assertEquals(AgentRun.Status.UNKNOWN, RunStatusDto(runId = "r", status = "teleporting").toDomain().status)
        assertEquals(AgentRun.Status.UNKNOWN, RunStatusDto(runId = "r", status = null).toDomain().status)
    }

    @Test fun steerGatedToRunningOnly() {
        assertTrue(AgentRun.Status.RUNNING.canSteer)
        assertFalse(AgentRun.Status.QUEUED.canSteer)
        assertFalse(AgentRun.Status.WAITING_FOR_APPROVAL.canSteer)
        assertFalse(AgentRun.Status.COMPLETED.canSteer)
    }

    @Test fun stopGatedToNonTerminalNonStopping() {
        assertTrue(AgentRun.Status.RUNNING.canStop)
        assertTrue(AgentRun.Status.QUEUED.canStop)
        assertTrue(AgentRun.Status.WAITING_FOR_APPROVAL.canStop)
        assertFalse(AgentRun.Status.STOPPING.canStop)
        assertFalse(AgentRun.Status.COMPLETED.canStop)
        assertFalse(AgentRun.Status.CANCELLED.canStop)
    }

    @Test fun terminalStatusesClassified() {
        assertTrue(AgentRun.Status.COMPLETED.isTerminal)
        assertTrue(AgentRun.Status.FAILED.isTerminal)
        assertTrue(AgentRun.Status.CANCELLED.isTerminal)
        assertTrue(AgentRun.Status.INTERRUPTED.isTerminal)
        assertFalse(AgentRun.Status.RUNNING.isTerminal)
        assertFalse(AgentRun.Status.STOPPING.isTerminal)
    }

    @Test fun approvalPendingFlag() {
        assertTrue(AgentRun.Status.WAITING_FOR_APPROVAL.isApprovalPending)
        assertFalse(AgentRun.Status.RUNNING.isApprovalPending)
    }

    @Test fun toleratesUnknownFutureFields() {
        val dto = json.decodeFromString(
            RunAdmissionDto.serializer(), """{"run_id":"r","status":"queued","brand_new":42}""")
        assertEquals("r", dto.toDomain().runId)
    }
}
