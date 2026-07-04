package com.bineesh.mooncartsettlement.matching.matcher

import com.bineesh.mooncartsettlement.config.MatchingProperties
import com.bineesh.mooncartsettlement.domain.enums.MatchMethod
import com.bineesh.mooncartsettlement.domain.model.BankSettlement
import com.bineesh.mooncartsettlement.domain.model.InternalOrder
import com.bineesh.mooncartsettlement.domain.model.MatchGroup
import com.bineesh.mooncartsettlement.domain.model.ReconciliationRun
import com.bineesh.mooncartsettlement.domain.model.YunoTransaction
import com.bineesh.mooncartsettlement.matching.model.MatchCandidate
import com.bineesh.mooncartsettlement.matching.model.MatchingContext
import com.bineesh.mooncartsettlement.matching.scorer.MatchScorer
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.ZoneOffset

@Component
class FuzzyMatcher(
    private val matchScorer: MatchScorer,
    private val properties: MatchingProperties,
) {
    fun matchBankToYuno(
        run: ReconciliationRun,
        bankRecords: List<BankSettlement>,
        yunoRecords: List<YunoTransaction>,
        orders: List<InternalOrder>,
        context: MatchingContext,
    ) {
        val unmatchedBanks = bankRecords.filter { it.matchGroup == null }
        val unmatchedYuno = yunoRecords.filter { it.matchGroup == null }
        val ordersByGroupId = orders.filter { it.matchGroup != null }.associateBy { it.matchGroup!!.id }

        val candidates = mutableListOf<MatchCandidate>()
        unmatchedBanks.forEach { bank ->
            val yunoCandidates = preFilterCandidates(bank, unmatchedYuno)
            yunoCandidates.forEach { yuno ->
                val order = yuno.matchGroup?.let { group -> ordersByGroupId[group.id] }
                val score = matchScorer.score(bank, yuno, order)
                if (score.total >= BigDecimal(properties.fuzzyMatchMinScore)) {
                    candidates.add(
                        MatchCandidate(
                            bankSettlementId = bank.id,
                            yunoTransactionId = yuno.id,
                            internalOrderId = order?.id,
                            score = score,
                        ),
                    )
                }
            }
        }

        candidates.sortByDescending { it.score.total }

        val assignedBanks = mutableSetOf<java.util.UUID>()
        val assignedYuno = mutableSetOf<java.util.UUID>()

        candidates.forEach { candidate ->
            if (candidate.bankSettlementId in assignedBanks || candidate.yunoTransactionId in assignedYuno) {
                return@forEach
            }

            val bank = bankRecords.first { it.id == candidate.bankSettlementId }
            val yuno = yunoRecords.first { it.id == candidate.yunoTransactionId }
            val order = candidate.internalOrderId?.let { id -> orders.first { it.id == id } }

            val group = yuno.matchGroup ?: MatchGroup(
                reconciliationRun = run,
                matchMethod = MatchMethod.FUZZY_SCORED,
            )

            bank.matchGroup = group
            yuno.matchGroup = group
            order?.matchGroup = group

            group.confidenceScore = candidate.score.total
            group.matchMethod = MatchMethod.FUZZY_SCORED
            group.settlementDelayDays = matchScorer.settlementDelayDays(bank, yuno).toInt()
            group.amountVariancePct = matchScorer.compareAmounts(
                yuno.amount,
                bank.settledAmount,
                yuno.currency,
            ).variancePct

            context.groupByBankId[bank.id] = group
            context.groupByYunoId[yuno.id] = group
            order?.let { context.groupByOrderId[it.id] = group }

            assignedBanks.add(bank.id)
            assignedYuno.add(yuno.id)
        }
    }

    fun linkOrphanYunoOrders(
        run: ReconciliationRun,
        yunoRecords: List<YunoTransaction>,
        orders: List<InternalOrder>,
        context: MatchingContext,
    ) {
        val unmatchedYuno = yunoRecords.filter { it.matchGroup == null }
        val unmatchedOrders = orders.filter { it.matchGroup == null }

        val candidates = mutableListOf<Triple<YunoTransaction, InternalOrder, BigDecimal>>()
        unmatchedYuno.forEach { yuno ->
            unmatchedOrders.forEach { order ->
                if (!canOrphanLink(yuno, order)) return@forEach
                val amountResult = matchScorer.compareAmounts(yuno.amount, order.orderAmount, yuno.currency)
                if (!amountResult.withinMatchTolerance) return@forEach
                val hoursApart = Duration.between(yuno.timestamp, order.timestamp).abs().toHours()
                if (hoursApart > properties.orphanLinkMaxHours) return@forEach
                candidates.add(Triple(yuno, order, amountResult.variancePct))
            }
        }

        val assignedYuno = mutableSetOf<java.util.UUID>()
        val assignedOrders = mutableSetOf<java.util.UUID>()

        candidates.sortedBy { it.third }.forEach { (yuno, order, variance) ->
            if (yuno.id in assignedYuno || order.id in assignedOrders) return@forEach
            val group = MatchGroup(
                reconciliationRun = run,
                confidenceScore = BigDecimal("70.00"),
                matchMethod = MatchMethod.ORPHAN_LINK,
                amountVariancePct = variance,
            )
            yuno.matchGroup = group
            order.matchGroup = group
            context.groupByYunoId[yuno.id] = group
            context.groupByOrderId[order.id] = group
            assignedYuno.add(yuno.id)
            assignedOrders.add(order.id)
        }
    }

    private fun preFilterCandidates(bank: BankSettlement, yunoRecords: List<YunoTransaction>): List<YunoTransaction> {
        val loosePct = BigDecimal("0.10")
        return yunoRecords
            .asSequence()
            .filter { it.currency == bank.currency }
            .filter { yuno ->
                val txDate = yuno.timestamp.atZone(ZoneOffset.UTC).toLocalDate()
                !bank.settlementDate.isBefore(txDate) &&
                    !bank.settlementDate.isAfter(txDate.plusDays(properties.settlementDelayMaxDays.toLong()))
            }
            .filter { yuno ->
                val diff = yuno.amount.subtract(bank.settledAmount).abs()
                val maxAmount = yuno.amount.max(bank.settledAmount)
                val variance = if (maxAmount.compareTo(BigDecimal.ZERO) == 0) {
                    BigDecimal.ZERO
                } else {
                    diff.divide(maxAmount, 6, RoundingMode.HALF_UP)
                }
                variance <= loosePct
            }
            .take(properties.maxCandidatesPerRecord)
            .toList()
    }

    private fun canOrphanLink(yuno: YunoTransaction, order: InternalOrder): Boolean =
        yuno.currency == order.currency &&
            yuno.customerEmail.equals(order.customerEmail, ignoreCase = true)
}
