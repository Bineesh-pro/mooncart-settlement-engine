package com.bineesh.mooncartsettlement.matching.scorer

import com.bineesh.mooncartsettlement.config.MatchingProperties
import com.bineesh.mooncartsettlement.domain.model.BankSettlement
import com.bineesh.mooncartsettlement.domain.model.InternalOrder
import com.bineesh.mooncartsettlement.domain.model.YunoTransaction
import com.bineesh.mooncartsettlement.matching.model.AmountResult
import com.bineesh.mooncartsettlement.matching.model.MatchScore
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.max

@Component
class MatchScorer(
    private val properties: MatchingProperties,
) {
    fun score(bank: BankSettlement, yuno: YunoTransaction, order: InternalOrder?): MatchScore {
        val orderRefMatch = if (order != null && yuno.orderReference.equals(order.orderId, ignoreCase = true)) {
            BigDecimal("30")
        } else {
            BigDecimal.ZERO
        }

        val yunoIdMatch = if (
            bank.yunoTransactionId != null &&
            bank.yunoTransactionId.equals(yuno.yunoTransactionId, ignoreCase = true)
        ) {
            BigDecimal("30")
        } else {
            BigDecimal.ZERO
        }

        val emailMatch = if (order != null && yuno.customerEmail.equals(order.customerEmail, ignoreCase = true)) {
            BigDecimal("15")
        } else {
            BigDecimal.ZERO
        }

        val amountResult = compareAmounts(yuno.amount, bank.settledAmount, yuno.currency)
        val amountScore = amountProximityScore(amountResult.variancePct)

        val delayDays = settlementDelayDays(bank, yuno)
        val timingScore = timingProximityScore(delayDays)

        val total = orderRefMatch + yunoIdMatch + emailMatch + amountScore + timingScore
        return MatchScore(
            total = total.setScale(2, RoundingMode.HALF_UP),
            orderReferenceMatch = orderRefMatch,
            yunoIdMatch = yunoIdMatch,
            emailMatch = emailMatch,
            amountScore = amountScore,
            timingScore = timingScore,
        )
    }

    fun compareAmounts(expected: BigDecimal, actual: BigDecimal, currency: String): AmountResult {
        val diff = expected.subtract(actual).abs()
        val maxAmount = expected.max(actual)
        val variancePct = if (maxAmount.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO
        } else {
            diff.divide(maxAmount, 6, RoundingMode.HALF_UP)
        }

        val absTolerance = BigDecimal.valueOf(properties.amountAbsoluteTolerance[currency] ?: 0.01)
        val pctTolerance = BigDecimal.valueOf(properties.amountTolerancePct)
        val withinMatchTolerance =
            diff <= absTolerance || variancePct <= pctTolerance

        return AmountResult(withinMatchTolerance, variancePct)
    }

    fun isWithinDiscrepancyThreshold(variancePct: BigDecimal): Boolean =
        variancePct <= BigDecimal.valueOf(properties.amountDiscrepancyThresholdPct)

    fun settlementDelayDays(bank: BankSettlement, yuno: YunoTransaction): Long {
        val txDate = yuno.timestamp.atZone(ZoneOffset.UTC).toLocalDate()
        return ChronoUnit.DAYS.between(txDate, bank.settlementDate).coerceAtLeast(0)
    }

    fun isValidSettlementDelay(delayDays: Long): Boolean =
        delayDays in 0..properties.settlementDelayMaxDays.toLong()

    fun isTimingAnomaly(delayDays: Long): Boolean =
        delayDays > properties.timingAnomalyThresholdDays.toLong()

    private fun amountProximityScore(variancePct: BigDecimal): BigDecimal {
        val tolerance = BigDecimal.valueOf(properties.amountTolerancePct)
        if (tolerance.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        val ratio = variancePct.divide(tolerance, 6, RoundingMode.HALF_UP)
        val score = BigDecimal.ONE.subtract(ratio).coerceAtLeast(BigDecimal.ZERO)
        return score.multiply(BigDecimal("15")).setScale(2, RoundingMode.HALF_UP)
    }

    private fun timingProximityScore(delayDays: Long): BigDecimal {
        val maxDays = max(properties.settlementDelayMaxDays, 1).toLong()
        val ratio = BigDecimal.valueOf(delayDays.toDouble() / maxDays.toDouble())
        val score = BigDecimal.ONE.subtract(ratio).coerceAtLeast(BigDecimal.ZERO)
        return score.multiply(BigDecimal("10")).setScale(2, RoundingMode.HALF_UP)
    }

    private fun BigDecimal.coerceAtLeast(min: BigDecimal): BigDecimal =
        if (this < min) min else this
}
