package com.bineesh.mooncartsettlement.matching

import com.bineesh.mooncartsettlement.domain.enums.ReconciliationRunStatus
import com.bineesh.mooncartsettlement.domain.model.ReconciliationRun
import com.bineesh.mooncartsettlement.ingestion.IngestService
import com.bineesh.mooncartsettlement.ingestion.dto.CreateReconciliationRunRequest
import com.bineesh.mooncartsettlement.ingestion.parser.CsvIngestParser
import com.bineesh.mooncartsettlement.reconciliation.ReconciliationRunService
import com.bineesh.mooncartsettlement.reconciliation.ReconciliationService
import com.bineesh.mooncartsettlement.testdata.TestDataGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.io.FileInputStream
import java.util.UUID

@SpringBootTest
@Transactional
class MatchingEngineIntegrationTest @Autowired constructor(
    private val testDataGenerator: TestDataGenerator,
    private val reconciliationRunService: ReconciliationRunService,
    private val ingestService: IngestService,
    private val csvIngestParser: CsvIngestParser,
    private val reconciliationService: ReconciliationService,
) {
    @Test
    fun `full pipeline processes generated dataset`() {
        val files = testDataGenerator.generate(kotlin.io.path.createTempDirectory("settlement-test").toFile())

        val run = reconciliationRunService.createRun(
            CreateReconciliationRunRequest("2026-03-01", "2026-03-30"),
        )
        val runId = UUID.fromString(run.id)

        val yunoSummary = ingestService.ingestYunoTransactions(
            runId,
            csvIngestParser.parseYunoTransactions(FileInputStream(files.yunoFile)),
        )
        val bankSummary = ingestService.ingestBankSettlements(
            runId,
            csvIngestParser.parseBankSettlements(FileInputStream(files.bankFile)),
        )
        val orderSummary = ingestService.ingestInternalOrders(
            runId,
            csvIngestParser.parseInternalOrders(FileInputStream(files.orderFile)),
        )

        assertTrue(yunoSummary.accepted >= 1000)
        assertTrue(bankSummary.accepted > 800)
        assertTrue(orderSummary.accepted >= 1000)

        val report = reconciliationService.executeRun(runId)
        assertTrue(report.summary.totalYuno >= 1000)
        assertTrue(report.summary.fullyMatched > 0)
        assertTrue(report.summary.unmatchedYuno + report.summary.unmatchedSettlement > 0)
        assertTrue(report.summary.amountDiscrepancies >= 0)

        val fetchedReport = reconciliationService.getReport(runId)
        assertEquals(report.summary.totalYuno, fetchedReport.summary.totalYuno)
        assertEquals(report.summary.totalBank, fetchedReport.summary.totalBank)
        assertEquals(report.summary.totalOrders, fetchedReport.summary.totalOrders)
        assertEquals(report.summary.fullyMatched, fetchedReport.summary.fullyMatched)
        assertEquals(report.summary.unmatchedYuno, fetchedReport.summary.unmatchedYuno)
        assertEquals(report.summary.amountDiscrepancies, fetchedReport.summary.amountDiscrepancies)
    }
}
