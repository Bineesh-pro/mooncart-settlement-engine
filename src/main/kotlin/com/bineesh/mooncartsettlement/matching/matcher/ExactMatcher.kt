package com.bineesh.mooncartsettlement.matching.matcher

import com.bineesh.mooncartsettlement.domain.enums.MatchMethod
import com.bineesh.mooncartsettlement.domain.model.BankSettlement
import com.bineesh.mooncartsettlement.domain.model.InternalOrder
import com.bineesh.mooncartsettlement.domain.model.MatchGroup
import com.bineesh.mooncartsettlement.domain.model.ReconciliationRun
import com.bineesh.mooncartsettlement.domain.model.YunoTransaction
import com.bineesh.mooncartsettlement.matching.model.MatchingContext
import com.bineesh.mooncartsettlement.matching.scorer.MatchScorer
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class ExactMatcher(
    private val matchScorer: MatchScorer,
) {
    fun matchYunoToOrders(
        run: ReconciliationRun,
        yunoRecords: List<YunoTransaction>,
        orders: List<InternalOrder>,
        context: MatchingContext,
    ) {
        val ordersByOrderId = orders
            .filter { it.matchGroup == null }
            .associateBy { it.orderId.lowercase() }

        yunoRecords.filter { it.matchGroup == null }.forEach { yuno ->
            val order = ordersByOrderId[yuno.orderReference.lowercase()] ?: return@forEach
            if (order.matchGroup != null) return@forEach

            val group = MatchGroup(
                reconciliationRun = run,
                confidenceScore = BigDecimal("92.00"),
                matchMethod = MatchMethod.EXACT_ORDER_REF,
            )
            yuno.matchGroup = group
            order.matchGroup = group
            context.groupByYunoId[yuno.id] = group
            context.groupByOrderId[order.id] = group
        }
    }

    fun matchBankByYunoId(
        run: ReconciliationRun,
        bankRecords: List<BankSettlement>,
        yunoRecords: List<YunoTransaction>,
        context: MatchingContext,
    ) {
        val yunoByExternalId = yunoRecords.associateBy { it.yunoTransactionId.lowercase() }

        bankRecords.filter { it.matchGroup == null && it.yunoTransactionId != null }.forEach { bank ->
            val yuno = yunoByExternalId[bank.yunoTransactionId!!.lowercase()] ?: return@forEach

            val existingGroup = yuno.matchGroup ?: MatchGroup(
                reconciliationRun = run,
                confidenceScore = BigDecimal("95.00"),
                matchMethod = MatchMethod.EXACT_YUNO_ID,
            ).also { group ->
                yuno.matchGroup = group
                context.groupByYunoId[yuno.id] = group
            }

            bank.matchGroup = existingGroup
            existingGroup.confidenceScore = BigDecimal("95.00")
            existingGroup.matchMethod = MatchMethod.EXACT_YUNO_ID
            context.groupByBankId[bank.id] = existingGroup

            val delayDays = matchScorer.settlementDelayDays(bank, yuno).toInt()
            existingGroup.settlementDelayDays = delayDays
            val amountResult = matchScorer.compareAmounts(yuno.amount, bank.settledAmount, yuno.currency)
            existingGroup.amountVariancePct = amountResult.variancePct
        }
    }
}
