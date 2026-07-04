package com.bineesh.mooncartsettlement.reconciliation

import com.bineesh.mooncartsettlement.domain.enums.ReconciliationRunStatus
import com.bineesh.mooncartsettlement.domain.model.ReconciliationRun
import com.bineesh.mooncartsettlement.domain.repository.ReconciliationRunRepository
import com.bineesh.mooncartsettlement.ingestion.dto.CreateReconciliationRunRequest
import com.bineesh.mooncartsettlement.ingestion.dto.ReconciliationRunResponse
import com.bineesh.mooncartsettlement.ingestion.util.DateTimeParser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ReconciliationRunService(
    private val reconciliationRunRepository: ReconciliationRunRepository,
) {
    @Transactional
    fun createRun(request: CreateReconciliationRunRequest): ReconciliationRunResponse {
        val run = ReconciliationRun(
            periodStart = DateTimeParser.parseLocalDate(request.periodStart),
            periodEnd = DateTimeParser.parseLocalDate(request.periodEnd),
            status = ReconciliationRunStatus.CREATED,
        )
        return reconciliationRunRepository.save(run).toResponse()
    }

    @Transactional(readOnly = true)
    fun getRun(runId: UUID): ReconciliationRun =
        reconciliationRunRepository.findById(runId)
            .orElseThrow { NoSuchElementException("Reconciliation run not found: $runId") }

    private fun ReconciliationRun.toResponse() =
        ReconciliationRunResponse(
            id = id.toString(),
            periodStart = periodStart.toString(),
            periodEnd = periodEnd.toString(),
            status = status.name,
        )
}
