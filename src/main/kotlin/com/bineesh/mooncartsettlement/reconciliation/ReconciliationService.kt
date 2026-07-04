package com.bineesh.mooncartsettlement.reconciliation

import com.bineesh.mooncartsettlement.domain.enums.DiscrepancyType
import com.bineesh.mooncartsettlement.domain.enums.ReconciliationRunStatus
import com.bineesh.mooncartsettlement.domain.model.BankSettlement
import com.bineesh.mooncartsettlement.domain.model.Discrepancy
import com.bineesh.mooncartsettlement.domain.model.InternalOrder
import com.bineesh.mooncartsettlement.domain.model.MatchGroup
import com.bineesh.mooncartsettlement.domain.model.YunoTransaction
import com.bineesh.mooncartsettlement.domain.repository.BankSettlementRepository
import com.bineesh.mooncartsettlement.domain.repository.DiscrepancyRepository
import com.bineesh.mooncartsettlement.domain.repository.InternalOrderRepository
import com.bineesh.mooncartsettlement.domain.repository.MatchGroupRepository
import com.bineesh.mooncartsettlement.domain.repository.ReconciliationRunRepository
import com.bineesh.mooncartsettlement.domain.repository.YunoTransactionRepository
import com.bineesh.mooncartsettlement.matching.engine.MatchingEngine
import com.bineesh.mooncartsettlement.matching.scorer.MatchScorer
import com.bineesh.mooncartsettlement.reconciliation.dto.ReconciliationReportResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class ReconciliationService(
    private val reconciliationRunRepository: ReconciliationRunRepository,
    private val reconciliationRunService: ReconciliationRunService,
    private val matchingEngine: MatchingEngine,
    private val discrepancyRepository: DiscrepancyRepository,
    private val yunoTransactionRepository: YunoTransactionRepository,
    private val bankSettlementRepository: BankSettlementRepository,
    private val internalOrderRepository: InternalOrderRepository,
    private val matchGroupRepository: MatchGroupRepository,
    private val matchScorer: MatchScorer,
) {
    @Transactional
    fun executeRun(runId: UUID): ReconciliationReportResponse {
        val run = reconciliationRunService.getRun(runId)
        run.status = ReconciliationRunStatus.RUNNING
        run.startedAt = Instant.now()
        reconciliationRunRepository.save(run)

        val result = matchingEngine.execute(run)

        run.status = ReconciliationRunStatus.COMPLETED
        run.completedAt = Instant.now()
        reconciliationRunRepository.save(run)

        return buildReport(
            runId = runId,
            yunoRecords = result.yunoRecords,
            bankRecords = result.bankRecords,
            orders = result.orders,
            matchGroups = result.matchGroups,
            discrepancies = result.discrepancies,
        )
    }

    @Transactional(readOnly = true)
    fun getReport(runId: UUID): ReconciliationReportResponse {
        reconciliationRunService.getRun(runId)
        return buildReport(
            runId = runId,
            yunoRecords = yunoTransactionRepository.findByReconciliationRunId(runId),
            bankRecords = bankSettlementRepository.findByReconciliationRunId(runId),
            orders = internalOrderRepository.findByReconciliationRunId(runId),
            matchGroups = matchGroupRepository.findByReconciliationRunId(runId),
            discrepancies = discrepancyRepository.findByReconciliationRunId(runId),
        )
    }

    private fun buildReport(
        runId: UUID,
        yunoRecords: List<YunoTransaction>,
        bankRecords: List<BankSettlement>,
        orders: List<InternalOrder>,
        matchGroups: List<MatchGroup>,
        discrepancies: List<Discrepancy>,
    ): ReconciliationReportResponse {
        val yunoByGroup = yunoRecords.filter { it.matchGroup != null }.associateBy { it.matchGroup!!.id }
        val bankByGroup = bankRecords.filter { it.matchGroup != null }.associateBy { it.matchGroup!!.id }
        val orderByGroup = orders.filter { it.matchGroup != null }.associateBy { it.matchGroup!!.id }

        val fullyMatched = matchGroups.count { group ->
            val yuno = yunoByGroup[group.id]
            val bank = bankByGroup[group.id]
            val order = orderByGroup[group.id]
            yuno != null && bank != null && order != null &&
                yuno.currency == bank.currency &&
                matchScorer.isWithinDiscrepancyThreshold(
                    matchScorer.compareAmounts(yuno.amount, bank.settledAmount, yuno.currency).variancePct,
                )
        }

        return ReconciliationReportResponse(
            runId = runId.toString(),
            summary = ReconciliationReportResponse.Summary(
                totalYuno = yunoRecords.size,
                totalBank = bankRecords.size,
                totalOrders = orders.size,
                fullyMatched = fullyMatched,
                unmatchedYuno = discrepancies.count { it.type == DiscrepancyType.UNMATCHED_YUNO },
                unmatchedSettlement = discrepancies.count { it.type == DiscrepancyType.UNMATCHED_SETTLEMENT },
                unmatchedOrder = discrepancies.count { it.type == DiscrepancyType.UNMATCHED_ORDER },
                amountDiscrepancies = discrepancies.count { it.type == DiscrepancyType.AMOUNT_MISMATCH },
                timingAnomalies = discrepancies.count { it.type == DiscrepancyType.TIMING_ANOMALY },
                refundChargebackSuspected = discrepancies.count {
                    it.type == DiscrepancyType.REFUND_CHARGEBACK_SUSPECTED
                },
                totalUnmatchedAmount = sumUnmatchedAmounts(discrepancies),
            ),
            generatedAt = Instant.now().toString(),
        )
    }

    private fun sumUnmatchedAmounts(
        discrepancies: List<Discrepancy>,
    ): Map<String, BigDecimal> =
        discrepancies
            .filter {
                it.type in setOf(
                    DiscrepancyType.UNMATCHED_YUNO,
                    DiscrepancyType.UNMATCHED_SETTLEMENT,
                    DiscrepancyType.UNMATCHED_ORDER,
                )
            }
            .filter { it.currency != null && it.amount != null }
            .groupBy { it.currency!! }
            .mapValues { (_, items) -> items.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount!! } }
}
