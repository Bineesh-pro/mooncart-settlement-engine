package com.bineesh.mooncartsettlement.testdata

import org.springframework.stereotype.Component
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Random
import kotlin.math.roundToInt

/**
 * Generates reproducible settlement test data (seed=42).
 * ~85% normal cases, ~15% problem scenarios including severe outliers.
 */
@Component
class TestDataGenerator {
    fun generate(outputDir: File, transactionCount: Int = 1050): GeneratedFiles {
        require(transactionCount >= 1000) { "transactionCount must be at least 1000" }
        outputDir.mkdirs()

        val random = Random(42)
        val startDate = LocalDate.of(2026, 3, 1)
        val endDate = LocalDate.of(2026, 3, 30)

        val yunoLines = mutableListOf(yunoHeader())
        val bankLines = mutableListOf(bankHeader())
        val orderLines = mutableListOf(orderHeader())

        val problemIndices = pickProblemIndices(random, transactionCount)
        val severeIndices = problemIndices.take(8).toSet()

        for (i in 1..transactionCount) {
            val scenario = scenarioFor(i, problemIndices, severeIndices)
            val currency = pickCurrency(random)
            val amount = generateAmount(random, currency, scenario.isSevere)
            val txDateTime = randomDateTime(random, startDate, endDate, scenario.isSevere)
            val orderId = "ORD-${i.toString().padStart(5, '0')}"
            val yunoId = "YUNO-${i.toString().padStart(5, '0')}"
            val bankRef = "BNK-${i.toString().padStart(5, '0')}"
            val merchantId = "MCH-${random.nextInt(20).plus(1)}"
            val email = "customer${i}@example.com"

            val yunoStatus = when {
                scenario.isRefund -> "REFUNDED"
                random.nextDouble() < 0.1 -> "AUTHORIZED"
                else -> "CAPTURED"
            }

            yunoLines.add(
                listOf(
                    yunoId,
                    txDateTime.toInstant(ZoneOffset.UTC).toString(),
                    amount.toPlainString(),
                    currency,
                    yunoStatus,
                    merchantId,
                    email,
                    orderId,
                ).joinToString(","),
            )

            orderLines.add(
                listOf(
                    orderId,
                    email,
                    amount.toPlainString(),
                    currency,
                    txDateTime.toInstant(ZoneOffset.UTC).toString(),
                    if (scenario.isUnmatchedOrder) "PENDING" else "PAID",
                ).joinToString(","),
            )

            when {
                scenario.isUnmatchedYuno -> Unit
                scenario.isUnmatchedSettlement -> {
                    if (random.nextBoolean()) {
                        bankLines.add(
                            orphanSettlement(random, i, currency, amount, startDate, endDate),
                        )
                    }
                }
                else -> {
                    val delayDays = when {
                        scenario.isLongDelay -> 3 + random.nextInt(3)
                        else -> 1 + random.nextInt(2)
                    }
                    val settlementDate = txDateTime.toLocalDate().plusDays(delayDays.toLong())
                    val settledAmount = when {
                        scenario.isAmountMismatch -> amount.multiply(BigDecimal.valueOf(0.96 + random.nextDouble() * 0.03))
                            .setScale(scale(currency), RoundingMode.HALF_UP)
                        scenario.isRefund -> amount.multiply(BigDecimal("0.50")).setScale(scale(currency), RoundingMode.HALF_UP)
                        else -> amount.multiply(BigDecimal.ONE.subtract(feeRate(random)))
                            .setScale(scale(currency), RoundingMode.HALF_UP)
                    }
                    val includeYunoId = !scenario.omitBankYunoId
                    bankLines.add(
                        listOf(
                            bankRef,
                            settlementDate.toString(),
                            settledAmount.toPlainString(),
                            currency,
                            if (includeYunoId) yunoId else "",
                        ).joinToString(","),
                    )
                }
            }
        }

        val yunoFile = File(outputDir, "yuno_transactions.csv")
        val bankFile = File(outputDir, "bank_settlements.csv")
        val orderFile = File(outputDir, "internal_orders.csv")
        yunoFile.writeText(yunoLines.joinToString("\n"))
        bankFile.writeText(bankLines.joinToString("\n"))
        orderFile.writeText(orderLines.joinToString("\n"))

        return GeneratedFiles(yunoFile, bankFile, orderFile, transactionCount)
    }

    private fun pickProblemIndices(random: Random, count: Int): Set<Int> {
        val target = (count * 0.15).roundToInt()
        return (1..count).shuffled(random).take(target).toSet()
    }

    private fun scenarioFor(index: Int, problems: Set<Int>, severe: Set<Int>): Scenario {
        if (index !in problems) return Scenario()
        val bucket = index % 10
        return Scenario(
            isUnmatchedYuno = bucket == 0 || bucket == 1,
            isUnmatchedSettlement = bucket == 2,
            isAmountMismatch = bucket == 3 || bucket == 4,
            isLongDelay = bucket == 5,
            isRefund = bucket == 6,
            omitBankYunoId = bucket == 7 || bucket == 8,
            isUnmatchedOrder = bucket == 9,
            isSevere = index in severe,
        )
    }

    private fun pickCurrency(random: Random): String {
        val roll = random.nextDouble()
        return when {
            roll < 0.40 -> "IDR"
            roll < 0.75 -> "PHP"
            else -> "USD"
        }
    }

    private fun generateAmount(random: Random, currency: String, severe: Boolean): BigDecimal {
        val base = when (currency) {
            "IDR" -> BigDecimal.valueOf(if (severe) 80_000_000 else random.nextInt(5_000_000).plus(50_000).toLong())
            "PHP" -> BigDecimal.valueOf(if (severe) 300_000 else random.nextInt(20_000).plus(500).toLong())
            else -> BigDecimal.valueOf(if (severe) 7500 else random.nextInt(900).plus(20).toLong())
        }
        return base.setScale(scale(currency), RoundingMode.HALF_UP)
    }

    private fun randomDateTime(random: Random, start: LocalDate, end: LocalDate, severe: Boolean): LocalDateTime {
        val days = end.toEpochDay() - start.toEpochDay()
        val dayOffset = if (severe) 0 else random.nextInt(days.toInt().plus(1))
        val date = start.plusDays(dayOffset.toLong())
        return date.atTime(random.nextInt(24), random.nextInt(60))
    }

    private fun feeRate(random: Random): BigDecimal =
        BigDecimal.valueOf(0.01 + random.nextDouble() * 0.02).setScale(4, RoundingMode.HALF_UP)

    private fun scale(currency: String): Int = if (currency == "IDR") 0 else 2

    private fun orphanSettlement(
        random: Random,
        index: Int,
        currency: String,
        amount: BigDecimal,
        start: LocalDate,
        end: LocalDate,
    ): String {
        val settlementDate = start.plusDays(random.nextInt((end.toEpochDay() - start.toEpochDay()).toInt()).toLong())
        return listOf(
            "ORPHAN-${index.toString().padStart(5, '0')}",
            settlementDate.toString(),
            amount.multiply(BigDecimal("0.97")).setScale(scale(currency), RoundingMode.HALF_UP).toPlainString(),
            currency,
            "",
        ).joinToString(",")
    }

    private fun yunoHeader() =
        "yuno_transaction_id,timestamp,amount,currency,status,merchant_id,customer_email,order_reference"

    private fun bankHeader() =
        "bank_reference_number,settlement_date,settled_amount,currency,yuno_transaction_id"

    private fun orderHeader() =
        "order_id,customer_email,order_amount,currency,timestamp,payment_status"

    data class GeneratedFiles(
        val yunoFile: File,
        val bankFile: File,
        val orderFile: File,
        val transactionCount: Int,
    )

    private data class Scenario(
        val isUnmatchedYuno: Boolean = false,
        val isUnmatchedSettlement: Boolean = false,
        val isAmountMismatch: Boolean = false,
        val isLongDelay: Boolean = false,
        val isRefund: Boolean = false,
        val omitBankYunoId: Boolean = false,
        val isUnmatchedOrder: Boolean = false,
        val isSevere: Boolean = false,
    )
}
