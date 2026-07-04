package com.bineesh.mooncartsettlement.matching.matcher

import com.bineesh.mooncartsettlement.config.MatchingProperties
import com.bineesh.mooncartsettlement.domain.enums.MatchMethod
import com.bineesh.mooncartsettlement.domain.enums.PaymentStatus
import com.bineesh.mooncartsettlement.domain.enums.YunoTransactionStatus
import com.bineesh.mooncartsettlement.domain.model.BankSettlement
import com.bineesh.mooncartsettlement.domain.model.InternalOrder
import com.bineesh.mooncartsettlement.domain.model.ReconciliationRun
import com.bineesh.mooncartsettlement.domain.model.YunoTransaction
import com.bineesh.mooncartsettlement.matching.model.MatchingContext
import com.bineesh.mooncartsettlement.matching.scorer.MatchScorer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ExactMatcherTest {
    private val matcher = ExactMatcher(MatchScorer(MatchingProperties()))

    @Test
    fun `links yuno and order by order reference`() {
        val run = ReconciliationRun(periodStart = LocalDate.parse("2026-03-01"), periodEnd = LocalDate.parse("2026-03-31"))
        val yuno = sampleYuno(run, "Y1", "ORD-1")
        val order = sampleOrder(run, "ORD-1")
        val context = emptyContext(listOf(yuno), emptyList(), listOf(order))

        matcher.matchYunoToOrders(run, listOf(yuno), listOf(order), context)

        assertNotNull(yuno.matchGroup)
        assertEquals(yuno.matchGroup, order.matchGroup)
        assertEquals(MatchMethod.EXACT_ORDER_REF, yuno.matchGroup?.matchMethod)
    }

    @Test
    fun `links bank to yuno by yuno transaction id`() {
        val run = ReconciliationRun(periodStart = LocalDate.parse("2026-03-01"), periodEnd = LocalDate.parse("2026-03-31"))
        val yuno = sampleYuno(run, "Y1", "ORD-1")
        val bank = sampleBank(run, "B1", "Y1")
        val context = emptyContext(listOf(yuno), listOf(bank), emptyList())

        matcher.matchBankByYunoId(run, listOf(bank), listOf(yuno), context)

        assertNotNull(bank.matchGroup)
        assertEquals(yuno.matchGroup, bank.matchGroup)
        assertEquals(MatchMethod.EXACT_YUNO_ID, bank.matchGroup?.matchMethod)
    }

    private fun sampleYuno(run: ReconciliationRun, yunoId: String, orderRef: String) =
        YunoTransaction(
            yunoTransactionId = yunoId,
            timestamp = Instant.parse("2026-03-10T10:00:00Z"),
            amount = BigDecimal("100.00"),
            currency = "USD",
            status = YunoTransactionStatus.CAPTURED,
            merchantId = "M1",
            customerEmail = "a@test.com",
            orderReference = orderRef,
            reconciliationRun = run,
        )

    private fun sampleOrder(run: ReconciliationRun, orderId: String) =
        InternalOrder(
            orderId = orderId,
            customerEmail = "a@test.com",
            orderAmount = BigDecimal("100.00"),
            currency = "USD",
            timestamp = Instant.parse("2026-03-10T10:05:00Z"),
            paymentStatus = PaymentStatus.PAID,
            reconciliationRun = run,
        )

    private fun sampleBank(run: ReconciliationRun, bankRef: String, yunoId: String) =
        BankSettlement(
            bankReferenceNumber = bankRef,
            settlementDate = LocalDate.parse("2026-03-11"),
            settledAmount = BigDecimal("98.00"),
            currency = "USD",
            yunoTransactionId = yunoId,
            reconciliationRun = run,
        )

    private fun emptyContext(
        yuno: List<YunoTransaction>,
        bank: List<BankSettlement>,
        orders: List<InternalOrder>,
    ) = MatchingContext(
        yunoById = yuno.associateBy { it.id },
        bankById = bank.associateBy { it.id },
        orderById = orders.associateBy { it.id },
        groupByYunoId = mutableMapOf(),
        groupByBankId = mutableMapOf(),
        groupByOrderId = mutableMapOf(),
    )
}
