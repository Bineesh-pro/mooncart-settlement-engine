package com.bineesh.mooncartsettlement.api.dto

import com.bineesh.mooncartsettlement.domain.enums.DiscrepancySeverity
import com.bineesh.mooncartsettlement.domain.enums.DiscrepancyType
import java.math.BigDecimal

data class DiscrepancyResponse(
    val id: String,
    val runId: String,
    val matchGroupId: String?,
    val type: DiscrepancyType,
    val severity: DiscrepancySeverity,
    val sourceEntityType: String,
    val sourceEntityId: String,
    val currency: String?,
    val amount: BigDecimal?,
    val merchantId: String?,
    val details: Map<String, Any?>?,
    val investigationPriority: BigDecimal?,
    val createdAt: String,
)

data class MatchDetailResponse(
    val matchGroupId: String,
    val confidenceScore: BigDecimal?,
    val matchMethod: String?,
    val amountVariancePct: BigDecimal?,
    val settlementDelayDays: Int?,
    val yuno: YunoDetail?,
    val bank: BankDetail?,
    val order: OrderDetail?,
)

data class YunoDetail(
    val id: String,
    val yunoTransactionId: String,
    val timestamp: String,
    val amount: BigDecimal,
    val currency: String,
    val status: String,
    val merchantId: String,
    val customerEmail: String,
    val orderReference: String,
)

data class BankDetail(
    val id: String,
    val bankReferenceNumber: String,
    val settlementDate: String,
    val settledAmount: BigDecimal,
    val currency: String,
    val yunoTransactionId: String?,
)

data class OrderDetail(
    val id: String,
    val orderId: String,
    val customerEmail: String,
    val orderAmount: BigDecimal,
    val currency: String,
    val timestamp: String,
    val paymentStatus: String,
)

data class StatisticsSummaryResponse(
    val totalDiscrepancies: Long,
    val byType: Map<String, Long>,
    val bySeverity: Map<String, Long>,
    val unmatchedAmountByCurrency: Map<String, BigDecimal>,
)

data class TrendPoint(
    val date: String,
    val count: Long,
)

data class StatisticsTrendsResponse(
    val trends: Map<String, List<TrendPoint>>,
)

data class InvestigationQueueItem(
    val discrepancyId: String,
    val type: DiscrepancyType,
    val severity: DiscrepancySeverity,
    val currency: String?,
    val amount: BigDecimal?,
    val merchantId: String?,
    val investigationPriority: BigDecimal?,
    val details: Map<String, Any?>?,
)

data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
