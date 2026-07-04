package com.bineesh.mooncartsettlement.ingestion.dto

import com.bineesh.mooncartsettlement.domain.enums.PaymentStatus
import com.bineesh.mooncartsettlement.domain.enums.YunoTransactionStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal

data class YunoTransactionIngestDto(
    @field:NotBlank val yunoTransactionId: String,
    @field:NotBlank val timestamp: String,
    @field:NotNull val amount: BigDecimal,
    @field:NotBlank
    @field:Pattern(regexp = "IDR|PHP|USD")
    val currency: String,
    @field:NotNull val status: YunoTransactionStatus,
    @field:NotBlank val merchantId: String,
    @field:NotBlank @field:Email val customerEmail: String,
    @field:NotBlank val orderReference: String,
)

data class BankSettlementIngestDto(
    @field:NotBlank val bankReferenceNumber: String,
    @field:NotBlank val settlementDate: String,
    @field:NotNull val settledAmount: BigDecimal,
    @field:NotBlank
    @field:Pattern(regexp = "IDR|PHP|USD")
    val currency: String,
    val yunoTransactionId: String? = null,
)

data class InternalOrderIngestDto(
    @field:NotBlank val orderId: String,
    @field:NotBlank @field:Email val customerEmail: String,
    @field:NotNull val orderAmount: BigDecimal,
    @field:NotBlank
    @field:Pattern(regexp = "IDR|PHP|USD")
    val currency: String,
    @field:NotBlank val timestamp: String,
    @field:NotNull val paymentStatus: PaymentStatus,
)

data class IngestSummaryDto(
    val accepted: Int,
    val rejected: Int,
    val duplicates: Int,
    val errors: List<String> = emptyList(),
)

data class CreateReconciliationRunRequest(
    @field:NotBlank val periodStart: String,
    @field:NotBlank val periodEnd: String,
)

data class ReconciliationRunResponse(
    val id: String,
    val periodStart: String,
    val periodEnd: String,
    val status: String,
)
