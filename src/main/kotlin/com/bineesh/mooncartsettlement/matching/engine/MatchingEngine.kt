package com.bineesh.mooncartsettlement.matching.engine

import com.bineesh.mooncartsettlement.domain.model.BankSettlement
import com.bineesh.mooncartsettlement.domain.model.Discrepancy
import com.bineesh.mooncartsettlement.domain.model.InternalOrder
import com.bineesh.mooncartsettlement.domain.model.MatchGroup
import com.bineesh.mooncartsettlement.domain.model.ReconciliationRun
import com.bineesh.mooncartsettlement.domain.model.YunoTransaction
import com.bineesh.mooncartsettlement.domain.repository.BankSettlementRepository
import com.bineesh.mooncartsettlement.domain.repository.DiscrepancyRepository
import com.bineesh.mooncartsettlement.domain.repository.InternalOrderRepository
import com.bineesh.mooncartsettlement.domain.repository.MatchGroupRepository
import com.bineesh.mooncartsettlement.domain.repository.YunoTransactionRepository
import com.bineesh.mooncartsettlement.matching.classifier.DiscrepancyClassifier
import com.bineesh.mooncartsettlement.matching.matcher.ExactMatcher
import com.bineesh.mooncartsettlement.matching.matcher.FuzzyMatcher
import com.bineesh.mooncartsettlement.matching.model.MatchingContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MatchingEngine(
    private val yunoTransactionRepository: YunoTransactionRepository,
    private val bankSettlementRepository: BankSettlementRepository,
    private val internalOrderRepository: InternalOrderRepository,
    private val matchGroupRepository: MatchGroupRepository,
    private val discrepancyRepository: DiscrepancyRepository,
    private val exactMatcher: ExactMatcher,
    private val fuzzyMatcher: FuzzyMatcher,
    private val discrepancyClassifier: DiscrepancyClassifier,
) {
    @Transactional
    fun execute(run: ReconciliationRun): MatchingResult {
        val yunoRecords = yunoTransactionRepository.findByReconciliationRunId(run.id)
        val bankRecords = bankSettlementRepository.findByReconciliationRunId(run.id)
        val orders = internalOrderRepository.findByReconciliationRunId(run.id)

        yunoRecords.forEach { it.matchGroup = null }
        bankRecords.forEach { it.matchGroup = null }
        orders.forEach { it.matchGroup = null }
        yunoTransactionRepository.saveAll(yunoRecords)
        bankSettlementRepository.saveAll(bankRecords)
        internalOrderRepository.saveAll(orders)

        discrepancyRepository.deleteAll(discrepancyRepository.findByReconciliationRunId(run.id))
        matchGroupRepository.deleteAll(matchGroupRepository.findByReconciliationRunId(run.id))

        val context = MatchingContext(
            yunoById = yunoRecords.associateBy { it.id },
            bankById = bankRecords.associateBy { it.id },
            orderById = orders.associateBy { it.id },
            groupByYunoId = mutableMapOf(),
            groupByBankId = mutableMapOf(),
            groupByOrderId = mutableMapOf(),
        )

        exactMatcher.matchYunoToOrders(run, yunoRecords, orders, context)
        exactMatcher.matchBankByYunoId(run, bankRecords, yunoRecords, context)
        fuzzyMatcher.matchBankToYuno(run, bankRecords, yunoRecords, orders, context)
        fuzzyMatcher.linkOrphanYunoOrders(run, yunoRecords, orders, context)

        val yunoGroupIds = yunoRecords.associate { it.id to it.matchGroup?.id }
        val bankGroupIds = bankRecords.associate { it.id to it.matchGroup?.id }
        val orderGroupIds = orders.associate { it.id to it.matchGroup?.id }

        val groups = collectGroups(yunoRecords, bankRecords, orders)
        yunoRecords.forEach { it.matchGroup = null }
        bankRecords.forEach { it.matchGroup = null }
        orders.forEach { it.matchGroup = null }

        val savedGroupById = matchGroupRepository.saveAllAndFlush(groups).associateBy { it.id }
        yunoRecords.forEach { yuno ->
            yuno.matchGroup = yunoGroupIds[yuno.id]?.let { savedGroupById[it] }
        }
        bankRecords.forEach { bank ->
            bank.matchGroup = bankGroupIds[bank.id]?.let { savedGroupById[it] }
        }
        orders.forEach { order ->
            order.matchGroup = orderGroupIds[order.id]?.let { savedGroupById[it] }
        }

        yunoTransactionRepository.saveAll(yunoRecords)
        bankSettlementRepository.saveAll(bankRecords)
        internalOrderRepository.saveAll(orders)

        val savedGroups = savedGroupById.values.toList()
        val discrepancies = discrepancyClassifier.classify(run, yunoRecords, bankRecords, orders, savedGroups)
        discrepancyRepository.saveAll(discrepancies)

        return MatchingResult(
            matchGroups = savedGroups,
            discrepancies = discrepancies,
            yunoRecords = yunoRecords,
            bankRecords = bankRecords,
            orders = orders,
        )
    }

    private fun collectGroups(
        yunoRecords: List<YunoTransaction>,
        bankRecords: List<BankSettlement>,
        orders: List<InternalOrder>,
    ): List<MatchGroup> =
        (yunoRecords.mapNotNull { it.matchGroup } +
            bankRecords.mapNotNull { it.matchGroup } +
            orders.mapNotNull { it.matchGroup })
            .distinctBy { it.id }
}

data class MatchingResult(
    val matchGroups: List<MatchGroup>,
    val discrepancies: List<Discrepancy>,
    val yunoRecords: List<YunoTransaction>,
    val bankRecords: List<BankSettlement>,
    val orders: List<InternalOrder>,
)
