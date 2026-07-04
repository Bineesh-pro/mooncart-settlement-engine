package com.bineesh.mooncartsettlement.api

import com.bineesh.mooncartsettlement.api.dto.DiscrepancyResponse
import com.bineesh.mooncartsettlement.api.dto.InvestigationQueueItem
import com.bineesh.mooncartsettlement.api.dto.MatchDetailResponse
import com.bineesh.mooncartsettlement.api.dto.PagedResponse
import com.bineesh.mooncartsettlement.api.dto.StatisticsSummaryResponse
import com.bineesh.mooncartsettlement.api.dto.StatisticsTrendsResponse
import com.bineesh.mooncartsettlement.domain.enums.DiscrepancyType
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class InvestigationController(
    private val investigationService: InvestigationService,
) {
    @GetMapping("/discrepancies")
    fun queryDiscrepancies(
        @RequestParam(required = false) runId: UUID?,
        @RequestParam(required = false) type: DiscrepancyType?,
        @RequestParam(required = false) currency: String?,
        @RequestParam(required = false) merchantId: String?,
        @RequestParam(required = false) minAmount: BigDecimal?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PagedResponse<DiscrepancyResponse> =
        investigationService.queryDiscrepancies(runId, type, currency, merchantId, minAmount, from, to, page, size)

    @GetMapping("/discrepancies/{id}")
    fun getDiscrepancy(@PathVariable id: UUID): DiscrepancyResponse =
        investigationService.getDiscrepancy(id)

    @GetMapping("/matches/{matchGroupId}")
    fun getMatchDetail(@PathVariable matchGroupId: UUID): MatchDetailResponse =
        investigationService.getMatchDetail(matchGroupId)

    @GetMapping("/statistics/summary")
    fun getSummary(@RequestParam(required = false) runId: UUID?): StatisticsSummaryResponse =
        investigationService.getSummary(runId)

    @GetMapping("/statistics/trends")
    fun getTrends(
        @RequestParam(required = false) runId: UUID?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): StatisticsTrendsResponse =
        investigationService.getTrends(runId, from, to)

    @GetMapping("/investigation-queue")
    fun getInvestigationQueue(
        @RequestParam(required = false) runId: UUID?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<InvestigationQueueItem> =
        investigationService.getInvestigationQueue(runId, limit)
}
