package com.bineesh.mooncartsettlement.reconciliation.dto

import java.math.BigDecimal

data class ReconciliationReportResponse(
    val runId: String,
    val summary: Summary,
    val generatedAt: String,
) {
    data class Summary(
        val totalYuno: Int,
        val totalBank: Int,
        val totalOrders: Int,
        val fullyMatched: Int,
        val unmatchedYuno: Int,
        val unmatchedSettlement: Int,
        val unmatchedOrder: Int,
        val amountDiscrepancies: Int,
        val timingAnomalies: Int,
        val refundChargebackSuspected: Int,
        val totalUnmatchedAmount: Map<String, BigDecimal>,
    )
}
