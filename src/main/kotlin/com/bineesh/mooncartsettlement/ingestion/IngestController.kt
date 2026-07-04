package com.bineesh.mooncartsettlement.ingestion

import com.bineesh.mooncartsettlement.ingestion.dto.CreateReconciliationRunRequest
import com.bineesh.mooncartsettlement.ingestion.dto.IngestSummaryDto
import com.bineesh.mooncartsettlement.ingestion.dto.ReconciliationRunResponse
import com.bineesh.mooncartsettlement.ingestion.parser.CsvIngestParser
import com.bineesh.mooncartsettlement.reconciliation.ReconciliationRunService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class IngestController(
    private val ingestService: IngestService,
    private val csvIngestParser: CsvIngestParser,
    private val reconciliationRunService: ReconciliationRunService,
) {
    @PostMapping("/reconciliation/runs")
    fun createRun(@Valid @RequestBody request: CreateReconciliationRunRequest): ReconciliationRunResponse =
        reconciliationRunService.createRun(request)

    @PostMapping("/ingest/yuno", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun ingestYuno(
        @RequestParam runId: UUID,
        @RequestPart("file") file: MultipartFile,
    ): IngestSummaryDto =
        ingestService.ingestYunoTransactions(runId, csvIngestParser.parseYunoTransactions(file.inputStream))

    @PostMapping("/ingest/bank-settlements", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun ingestBankSettlements(
        @RequestParam runId: UUID,
        @RequestPart("file") file: MultipartFile,
    ): IngestSummaryDto =
        ingestService.ingestBankSettlements(runId, csvIngestParser.parseBankSettlements(file.inputStream))

    @PostMapping("/ingest/orders", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun ingestOrders(
        @RequestParam runId: UUID,
        @RequestPart("file") file: MultipartFile,
    ): IngestSummaryDto =
        ingestService.ingestInternalOrders(runId, csvIngestParser.parseInternalOrders(file.inputStream))
}
