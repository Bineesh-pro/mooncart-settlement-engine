package com.bineesh.mooncartsettlement.matching.classifier

import com.bineesh.mooncartsettlement.domain.enums.DiscrepancySeverity
import com.bineesh.mooncartsettlement.domain.enums.DiscrepancyType
import com.bineesh.mooncartsettlement.domain.enums.PaymentStatus
import com.bineesh.mooncartsettlement.domain.enums.SourceEntityType
import com.bineesh.mooncartsettlement.domain.enums.YunoTransactionStatus
import com.bineesh.mooncartsettlement.domain.model.BankSettlement
import com.bineesh.mooncartsettlement.domain.model.Discrepancy
import com.bineesh.mooncartsettlement.domain.model.InternalOrder
import com.bineesh.mooncartsettlement.domain.model.MatchGroup
import com.bineesh.mooncartsettlement.domain.model.ReconciliationRun
import com.bineesh.mooncartsettlement.domain.model.YunoTransaction
import com.bineesh.mooncartsettlement.matching.scorer.MatchScorer
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class DiscrepancyClassifier(
    private val matchScorer: MatchScorer,
) {
    fun classify(
        run: ReconciliationRun,
        yunoRecords: List<YunoTransaction>,
        bankRecords: List<BankSettlement>,
        orders: List<InternalOrder>,
        groups: List<MatchGroup>,
    ): List<Discrepancy> {
        val discrepancies = mutableListOf<Discrepancy>()

        yunoRecords.filter { it.status == YunoTransactionStatus.CAPTURED && it.matchGroup == null }.forEach { yuno ->
            discrepancies.add(
                createDiscrepancy(
                    run = run,
                    type = DiscrepancyType.UNMATCHED_YUNO,
                    severity = severityForAmount(yuno.amount, yuno.currency),
                    sourceEntityType = SourceEntityType.YUNO_TRANSACTION,
                    sourceEntityId = yuno.id,
                    currency = yuno.currency,
                    amount = yuno.amount,
                    merchantId = yuno.merchantId,
                    details = mapOf(
                        "yunoTransactionId" to yuno.yunoTransactionId,
                        "orderReference" to yuno.orderReference,
                        "timestamp" to yuno.timestamp.toString(),
                    ),
                    ageDays = ageDays(yuno.timestamp),
                ),
            )
        }

        bankRecords.filter { it.matchGroup == null }.forEach { bank ->
            discrepancies.add(
                createDiscrepancy(
                    run = run,
                    type = DiscrepancyType.UNMATCHED_SETTLEMENT,
                    severity = severityForAmount(bank.settledAmount, bank.currency),
                    sourceEntityType = SourceEntityType.BANK_SETTLEMENT,
                    sourceEntityId = bank.id,
                    currency = bank.currency,
                    amount = bank.settledAmount,
                    details = mapOf(
                        "bankReferenceNumber" to bank.bankReferenceNumber,
                        "settlementDate" to bank.settlementDate.toString(),
                        "yunoTransactionId" to bank.yunoTransactionId,
                    ),
                    ageDays = ageDays(bank.settlementDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)),
                ),
            )
        }

        orders.filter { it.paymentStatus == PaymentStatus.PAID && it.matchGroup == null }.forEach { order ->
            discrepancies.add(
                createDiscrepancy(
                    run = run,
                    type = DiscrepancyType.UNMATCHED_ORDER,
                    severity = severityForAmount(order.orderAmount, order.currency),
                    sourceEntityType = SourceEntityType.INTERNAL_ORDER,
                    sourceEntityId = order.id,
                    currency = order.currency,
                    amount = order.orderAmount,
                    details = mapOf(
                        "orderId" to order.orderId,
                        "customerEmail" to order.customerEmail,
                        "timestamp" to order.timestamp.toString(),
                    ),
                    ageDays = ageDays(order.timestamp),
                ),
            )
        }

        val yunoByGroup = yunoRecords.filter { it.matchGroup != null }.associateBy { it.matchGroup!!.id }
        val bankByGroup = bankRecords.filter { it.matchGroup != null }.associateBy { it.matchGroup!!.id }
        val orderByGroup = orders.filter { it.matchGroup != null }.associateBy { it.matchGroup!!.id }

        groups.forEach { group ->
            val yuno = yunoByGroup[group.id]
            val bank = bankByGroup[group.id]
            val order = orderByGroup[group.id]

            if (yuno != null && bank != null) {
                val amountResult = matchScorer.compareAmounts(yuno.amount, bank.settledAmount, yuno.currency)
                if (!matchScorer.isWithinDiscrepancyThreshold(amountResult.variancePct)) {
                    discrepancies.add(
                        createDiscrepancy(
                            run = run,
                            matchGroup = group,
                            type = DiscrepancyType.AMOUNT_MISMATCH,
                            severity = DiscrepancySeverity.MEDIUM,
                            sourceEntityType = SourceEntityType.MATCH_GROUP,
                            sourceEntityId = group.id,
                            currency = yuno.currency,
                            amount = yuno.amount,
                            merchantId = yuno.merchantId,
                            details = mapOf(
                                "yunoAmount" to yuno.amount,
                                "settledAmount" to bank.settledAmount,
                                "variancePct" to amountResult.variancePct,
                            ),
                            ageDays = ageDays(yuno.timestamp),
                        ),
                    )
                }

                val delayDays = matchScorer.settlementDelayDays(bank, yuno)
                if (matchScorer.isTimingAnomaly(delayDays)) {
                    discrepancies.add(
                        createDiscrepancy(
                            run = run,
                            matchGroup = group,
                            type = DiscrepancyType.TIMING_ANOMALY,
                            severity = DiscrepancySeverity.LOW,
                            sourceEntityType = SourceEntityType.MATCH_GROUP,
                            sourceEntityId = group.id,
                            currency = yuno.currency,
                            amount = yuno.amount,
                            merchantId = yuno.merchantId,
                            details = mapOf(
                                "settlementDelayDays" to delayDays,
                                "settlementDate" to bank.settlementDate.toString(),
                                "transactionTimestamp" to yuno.timestamp.toString(),
                            ),
                            ageDays = ageDays(yuno.timestamp),
                        ),
                    )
                }

                detectRefundChargeback(run, group, yuno, bank, bankRecords)?.let { discrepancies.add(it) }
            }
        }

        applyInvestigationPriorities(discrepancies, yunoRecords)
        return discrepancies
    }

    private fun detectRefundChargeback(
        run: ReconciliationRun,
        group: MatchGroup,
        yuno: YunoTransaction,
        bank: BankSettlement,
        allBankRecords: List<BankSettlement>,
    ): Discrepancy? {
        val amountResult = matchScorer.compareAmounts(yuno.amount, bank.settledAmount, yuno.currency)
        val shortfall = yuno.amount.subtract(bank.settledAmount)
        val shortfallPct = if (yuno.amount.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO
        } else {
            shortfall.divide(yuno.amount, 6, RoundingMode.HALF_UP)
        }

        val reason = when {
            yuno.status == YunoTransactionStatus.REFUNDED -> "REFUND"
            bank.settledAmount < BigDecimal.ZERO -> "CHARGEBACK"
            shortfallPct >= BigDecimal("0.10") && isPartialSettlement(yuno.amount, bank.settledAmount) -> "PARTIAL_SETTLEMENT"
            shortfallPct >= BigDecimal("0.10") &&
                allBankRecords.any {
                    it.settledAmount < BigDecimal.ZERO &&
                        it.currency == bank.currency &&
                        it.settlementDate == bank.settlementDate
                } -> "CHARGEBACK"
            else -> return null
        }

        return createDiscrepancy(
            run = run,
            matchGroup = group,
            type = DiscrepancyType.REFUND_CHARGEBACK_SUSPECTED,
            severity = DiscrepancySeverity.HIGH,
            sourceEntityType = SourceEntityType.MATCH_GROUP,
            sourceEntityId = group.id,
            currency = yuno.currency,
            amount = yuno.amount,
            merchantId = yuno.merchantId,
            details = mapOf(
                "reason" to reason,
                "yunoStatus" to yuno.status.name,
                "variancePct" to amountResult.variancePct,
                "settledAmount" to bank.settledAmount,
            ),
            ageDays = ageDays(yuno.timestamp),
        )
    }

    private fun isPartialSettlement(expected: BigDecimal, actual: BigDecimal): Boolean {
        if (expected.compareTo(BigDecimal.ZERO) == 0) return false
        val ratio = actual.divide(expected, 2, RoundingMode.HALF_UP)
        return ratio >= BigDecimal("0.40") && ratio <= BigDecimal("0.60")
    }

    private fun createDiscrepancy(
        run: ReconciliationRun,
        type: DiscrepancyType,
        severity: DiscrepancySeverity,
        sourceEntityType: SourceEntityType,
        sourceEntityId: java.util.UUID,
        currency: String? = null,
        amount: BigDecimal? = null,
        merchantId: String? = null,
        details: Map<String, Any?> = emptyMap(),
        matchGroup: MatchGroup? = null,
        ageDays: Long = 0,
    ): Discrepancy =
        Discrepancy(
            reconciliationRun = run,
            matchGroup = matchGroup,
            type = type,
            severity = severity,
            sourceEntityType = sourceEntityType,
            sourceEntityId = sourceEntityId,
            currency = currency,
            amount = amount,
            merchantId = merchantId,
            details = details,
            investigationPriority = computePriority(ageDays, amount ?: BigDecimal.ZERO, severity),
        )

    private fun severityForAmount(amount: BigDecimal, currency: String): DiscrepancySeverity {
        val normalized = when (currency) {
            "USD" -> amount
            "PHP" -> amount.divide(BigDecimal("56"), 2, RoundingMode.HALF_UP)
            "IDR" -> amount.divide(BigDecimal("16000"), 2, RoundingMode.HALF_UP)
            else -> amount
        }
        return when {
            normalized >= BigDecimal("5000") -> DiscrepancySeverity.CRITICAL
            normalized >= BigDecimal("1000") -> DiscrepancySeverity.HIGH
            normalized >= BigDecimal("100") -> DiscrepancySeverity.MEDIUM
            else -> DiscrepancySeverity.LOW
        }
    }

    private fun computePriority(ageDays: Long, amount: BigDecimal, severity: DiscrepancySeverity): BigDecimal {
        val ageScore = BigDecimal.valueOf(ageDays.toDouble()).min(BigDecimal("30"))
        val amountScore = amount.min(BigDecimal("10000")).divide(BigDecimal("10000"), 4, RoundingMode.HALF_UP)
        val severityScore = when (severity) {
            DiscrepancySeverity.CRITICAL -> BigDecimal("1.0")
            DiscrepancySeverity.HIGH -> BigDecimal("0.75")
            DiscrepancySeverity.MEDIUM -> BigDecimal("0.5")
            DiscrepancySeverity.LOW -> BigDecimal("0.25")
        }
        return ageScore.multiply(BigDecimal("0.4"))
            .add(amountScore.multiply(BigDecimal("100")).multiply(BigDecimal("0.4")))
            .add(severityScore.multiply(BigDecimal("0.2")))
            .setScale(4, RoundingMode.HALF_UP)
    }

    private fun applyInvestigationPriorities(
        discrepancies: List<Discrepancy>,
        yunoRecords: List<YunoTransaction>,
    ) {
        val merchantRates = discrepancies
            .mapNotNull { it.merchantId }
            .groupingBy { it }
            .eachCount()
            .mapValues { (_, count) -> count.toDouble() / discrepancies.size.coerceAtLeast(1) }

        discrepancies.forEach { discrepancy ->
            val merchantImpact = merchantRates[discrepancy.merchantId] ?: 0.0
            val current = discrepancy.investigationPriority ?: BigDecimal.ZERO
            discrepancy.investigationPriority = current
                .add(BigDecimal.valueOf(merchantImpact * 20))
                .setScale(4, RoundingMode.HALF_UP)
        }
    }

    private fun ageDays(instant: Instant): Long =
        ChronoUnit.DAYS.between(instant, Instant.now()).coerceAtLeast(0)

    private fun BigDecimal.min(other: BigDecimal): BigDecimal = if (this < other) this else other
}
