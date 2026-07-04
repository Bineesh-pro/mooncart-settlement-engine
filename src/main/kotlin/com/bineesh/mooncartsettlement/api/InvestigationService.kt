package com.bineesh.mooncartsettlement.api

import com.bineesh.mooncartsettlement.api.dto.BankDetail
import com.bineesh.mooncartsettlement.api.dto.DiscrepancyResponse
import com.bineesh.mooncartsettlement.api.dto.InvestigationQueueItem
import com.bineesh.mooncartsettlement.api.dto.MatchDetailResponse
import com.bineesh.mooncartsettlement.api.dto.OrderDetail
import com.bineesh.mooncartsettlement.api.dto.PagedResponse
import com.bineesh.mooncartsettlement.api.dto.StatisticsSummaryResponse
import com.bineesh.mooncartsettlement.api.dto.StatisticsTrendsResponse
import com.bineesh.mooncartsettlement.api.dto.TrendPoint
import com.bineesh.mooncartsettlement.api.dto.YunoDetail
import com.bineesh.mooncartsettlement.domain.enums.DiscrepancyType
import com.bineesh.mooncartsettlement.domain.model.Discrepancy
import com.bineesh.mooncartsettlement.domain.repository.BankSettlementRepository
import com.bineesh.mooncartsettlement.domain.repository.DiscrepancyRepository
import com.bineesh.mooncartsettlement.domain.repository.InternalOrderRepository
import com.bineesh.mooncartsettlement.domain.repository.MatchGroupRepository
import com.bineesh.mooncartsettlement.domain.repository.YunoTransactionRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
class InvestigationService(
    private val discrepancyRepository: DiscrepancyRepository,
    private val matchGroupRepository: MatchGroupRepository,
    private val yunoTransactionRepository: YunoTransactionRepository,
    private val bankSettlementRepository: BankSettlementRepository,
    private val internalOrderRepository: InternalOrderRepository,
) {
    @Transactional(readOnly = true)
    fun queryDiscrepancies(
        runId: UUID?,
        type: DiscrepancyType?,
        currency: String?,
        merchantId: String?,
        minAmount: BigDecimal?,
        from: LocalDate?,
        to: LocalDate?,
        page: Int,
        size: Int,
    ): PagedResponse<DiscrepancyResponse> {
        val spec = buildSpec(runId, type, currency, merchantId, minAmount, from, to)
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = discrepancyRepository.findAll(spec, pageable)
        return PagedResponse(
            items = result.content.map { it.toResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getDiscrepancy(id: UUID): DiscrepancyResponse =
        discrepancyRepository.findById(id)
            .map { it.toResponse() }
            .orElseThrow { NoSuchElementException("Discrepancy not found: $id") }

    @Transactional(readOnly = true)
    fun getMatchDetail(matchGroupId: UUID): MatchDetailResponse {
        val group = matchGroupRepository.findById(matchGroupId)
            .orElseThrow { NoSuchElementException("Match group not found: $matchGroupId") }

        val yuno = yunoTransactionRepository.findByReconciliationRunId(group.reconciliationRun.id)
            .firstOrNull { it.matchGroup?.id == matchGroupId }
        val bank = bankSettlementRepository.findByReconciliationRunId(group.reconciliationRun.id)
            .firstOrNull { it.matchGroup?.id == matchGroupId }
        val order = internalOrderRepository.findByReconciliationRunId(group.reconciliationRun.id)
            .firstOrNull { it.matchGroup?.id == matchGroupId }

        return MatchDetailResponse(
            matchGroupId = matchGroupId.toString(),
            confidenceScore = group.confidenceScore,
            matchMethod = group.matchMethod?.name,
            amountVariancePct = group.amountVariancePct,
            settlementDelayDays = group.settlementDelayDays,
            yuno = yuno?.let {
                YunoDetail(
                    id = it.id.toString(),
                    yunoTransactionId = it.yunoTransactionId,
                    timestamp = it.timestamp.toString(),
                    amount = it.amount,
                    currency = it.currency,
                    status = it.status.name,
                    merchantId = it.merchantId,
                    customerEmail = it.customerEmail,
                    orderReference = it.orderReference,
                )
            },
            bank = bank?.let {
                BankDetail(
                    id = it.id.toString(),
                    bankReferenceNumber = it.bankReferenceNumber,
                    settlementDate = it.settlementDate.toString(),
                    settledAmount = it.settledAmount,
                    currency = it.currency,
                    yunoTransactionId = it.yunoTransactionId,
                )
            },
            order = order?.let {
                OrderDetail(
                    id = it.id.toString(),
                    orderId = it.orderId,
                    customerEmail = it.customerEmail,
                    orderAmount = it.orderAmount,
                    currency = it.currency,
                    timestamp = it.timestamp.toString(),
                    paymentStatus = it.paymentStatus.name,
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun getSummary(runId: UUID?): StatisticsSummaryResponse {
        val discrepancies = if (runId != null) {
            discrepancyRepository.findByReconciliationRunId(runId)
        } else {
            discrepancyRepository.findAll()
        }

        val unmatchedTypes = setOf(
            DiscrepancyType.UNMATCHED_YUNO,
            DiscrepancyType.UNMATCHED_SETTLEMENT,
            DiscrepancyType.UNMATCHED_ORDER,
        )

        return StatisticsSummaryResponse(
            totalDiscrepancies = discrepancies.size.toLong(),
            byType = discrepancies.groupingBy { it.type.name }.eachCount().mapValues { it.value.toLong() },
            bySeverity = discrepancies.groupingBy { it.severity.name }.eachCount().mapValues { it.value.toLong() },
            unmatchedAmountByCurrency = discrepancies
                .filter { it.type in unmatchedTypes && it.currency != null && it.amount != null }
                .groupBy { it.currency!! }
                .mapValues { (_, items) -> items.fold(BigDecimal.ZERO) { acc, d -> acc + d.amount!! } },
        )
    }

    @Transactional(readOnly = true)
    fun getTrends(runId: UUID?, from: LocalDate?, to: LocalDate?): StatisticsTrendsResponse {
        val spec = buildSpec(runId, null, null, null, null, from, to)
        val discrepancies = discrepancyRepository.findAll(spec)

        val trends = discrepancies.groupBy { it.type.name }
            .mapValues { (_, items) ->
                items.groupBy { it.createdAt.atZone(ZoneOffset.UTC).toLocalDate() }
                    .entries
                    .sortedBy { it.key }
                    .map { (date, dayItems) -> TrendPoint(date.toString(), dayItems.size.toLong()) }
            }

        return StatisticsTrendsResponse(trends = trends)
    }

    @Transactional(readOnly = true)
    fun getInvestigationQueue(runId: UUID?, limit: Int): List<InvestigationQueueItem> {
        val spec = buildSpec(runId, null, null, null, null, null, null)
        return discrepancyRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "investigationPriority"))
            .take(limit)
            .map {
                InvestigationQueueItem(
                    discrepancyId = it.id.toString(),
                    type = it.type,
                    severity = it.severity,
                    currency = it.currency,
                    amount = it.amount,
                    merchantId = it.merchantId,
                    investigationPriority = it.investigationPriority,
                    details = it.details,
                )
            }
    }

    private fun buildSpec(
        runId: UUID?,
        type: DiscrepancyType?,
        currency: String?,
        merchantId: String?,
        minAmount: BigDecimal?,
        from: LocalDate?,
        to: LocalDate?,
    ): Specification<Discrepancy> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            runId?.let {
                predicates.add(cb.equal(root.get<Any>("reconciliationRun").get<UUID>("id"), it))
            }
            type?.let { predicates.add(cb.equal(root.get<DiscrepancyType>("type"), it)) }
            currency?.let { predicates.add(cb.equal(root.get<String>("currency"), it.uppercase())) }
            merchantId?.let { predicates.add(cb.equal(root.get<String>("merchantId"), it)) }
            minAmount?.let { predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), it)) }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)))
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)))
            }
            cb.and(*predicates.toTypedArray())
        }

    private fun Discrepancy.toResponse() =
        DiscrepancyResponse(
            id = id.toString(),
            runId = reconciliationRun.id.toString(),
            matchGroupId = matchGroup?.id?.toString(),
            type = type,
            severity = severity,
            sourceEntityType = sourceEntityType.name,
            sourceEntityId = sourceEntityId.toString(),
            currency = currency,
            amount = amount,
            merchantId = merchantId,
            details = details,
            investigationPriority = investigationPriority,
            createdAt = createdAt.toString(),
        )
}
