package com.bineesh.mooncartsettlement.reconciliation

import com.bineesh.mooncartsettlement.reconciliation.dto.ReconciliationReportResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/reconciliation/runs")
class ReconciliationController(
    private val reconciliationService: ReconciliationService,
) {
    @PostMapping("/{runId}/execute")
    fun executeRun(@PathVariable runId: UUID): ReconciliationReportResponse =
        reconciliationService.executeRun(runId)

    @GetMapping("/{runId}/report")
    fun getReport(@PathVariable runId: UUID): ReconciliationReportResponse =
        reconciliationService.getReport(runId)
}
