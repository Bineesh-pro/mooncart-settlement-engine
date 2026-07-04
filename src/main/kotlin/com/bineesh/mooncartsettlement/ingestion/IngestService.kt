package com.bineesh.mooncartsettlement.ingestion

import com.bineesh.mooncartsettlement.domain.enums.ReconciliationRunStatus
import com.bineesh.mooncartsettlement.domain.model.BankSettlement
import com.bineesh.mooncartsettlement.domain.model.InternalOrder
import com.bineesh.mooncartsettlement.domain.model.YunoTransaction
import com.bineesh.mooncartsettlement.domain.repository.BankSettlementRepository
import com.bineesh.mooncartsettlement.domain.repository.InternalOrderRepository
import com.bineesh.mooncartsettlement.domain.repository.YunoTransactionRepository
import com.bineesh.mooncartsettlement.ingestion.dto.BankSettlementIngestDto
import com.bineesh.mooncartsettlement.ingestion.dto.IngestSummaryDto
import com.bineesh.mooncartsettlement.ingestion.dto.InternalOrderIngestDto
import com.bineesh.mooncartsettlement.ingestion.dto.YunoTransactionIngestDto
import com.bineesh.mooncartsettlement.ingestion.util.CurrencyValidator
import com.bineesh.mooncartsettlement.ingestion.util.DateTimeParser
import com.bineesh.mooncartsettlement.reconciliation.ReconciliationRunService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class IngestService(
    private val reconciliationRunService: ReconciliationRunService,
    private val yunoTransactionRepository: YunoTransactionRepository,
    private val bankSettlementRepository: BankSettlementRepository,
    private val internalOrderRepository: InternalOrderRepository,
) {
    @Transactional
    fun ingestYunoTransactions(runId: UUID, records: List<YunoTransactionIngestDto>): IngestSummaryDto {
        val run = reconciliationRunService.getRun(runId)
        run.status = ReconciliationRunStatus.INGESTING
        return ingest(records) { dto, errors ->
            if (yunoTransactionRepository.existsByReconciliationRunIdAndYunoTransactionId(runId, dto.yunoTransactionId)) {
                return@ingest IngestOutcome.DUPLICATE
            }
            try {
                yunoTransactionRepository.save(
                    YunoTransaction(
                        yunoTransactionId = dto.yunoTransactionId.trim(),
                        timestamp = DateTimeParser.parseInstant(dto.timestamp),
                        amount = dto.amount,
                        currency = CurrencyValidator.validate(dto.currency),
                        status = dto.status,
                        merchantId = dto.merchantId.trim(),
                        customerEmail = dto.customerEmail.trim().lowercase(),
                        orderReference = dto.orderReference.trim(),
                        reconciliationRun = run,
                    ),
                )
                IngestOutcome.ACCEPTED
            } catch (ex: Exception) {
                errors.add("yunoTransactionId=${dto.yunoTransactionId}: ${ex.message}")
                IngestOutcome.REJECTED
            }
        }
    }

    @Transactional
    fun ingestBankSettlements(runId: UUID, records: List<BankSettlementIngestDto>): IngestSummaryDto {
        val run = reconciliationRunService.getRun(runId)
        run.status = ReconciliationRunStatus.INGESTING
        return ingest(records) { dto, errors ->
            if (bankSettlementRepository.existsByReconciliationRunIdAndBankReferenceNumber(runId, dto.bankReferenceNumber)) {
                return@ingest IngestOutcome.DUPLICATE
            }
            try {
                bankSettlementRepository.save(
                    BankSettlement(
                        bankReferenceNumber = dto.bankReferenceNumber.trim(),
                        settlementDate = DateTimeParser.parseLocalDate(dto.settlementDate),
                        settledAmount = dto.settledAmount,
                        currency = CurrencyValidator.validate(dto.currency),
                        yunoTransactionId = dto.yunoTransactionId?.trim(),
                        reconciliationRun = run,
                    ),
                )
                IngestOutcome.ACCEPTED
            } catch (ex: Exception) {
                errors.add("bankReferenceNumber=${dto.bankReferenceNumber}: ${ex.message}")
                IngestOutcome.REJECTED
            }
        }
    }

    @Transactional
    fun ingestInternalOrders(runId: UUID, records: List<InternalOrderIngestDto>): IngestSummaryDto {
        val run = reconciliationRunService.getRun(runId)
        run.status = ReconciliationRunStatus.INGESTING
        return ingest(records) { dto, errors ->
            if (internalOrderRepository.existsByReconciliationRunIdAndOrderId(runId, dto.orderId)) {
                return@ingest IngestOutcome.DUPLICATE
            }
            try {
                internalOrderRepository.save(
                    InternalOrder(
                        orderId = dto.orderId.trim(),
                        customerEmail = dto.customerEmail.trim().lowercase(),
                        orderAmount = dto.orderAmount,
                        currency = CurrencyValidator.validate(dto.currency),
                        timestamp = DateTimeParser.parseInstant(dto.timestamp),
                        paymentStatus = dto.paymentStatus,
                        reconciliationRun = run,
                    ),
                )
                IngestOutcome.ACCEPTED
            } catch (ex: Exception) {
                errors.add("orderId=${dto.orderId}: ${ex.message}")
                IngestOutcome.REJECTED
            }
        }
    }

    private fun <T> ingest(
        records: List<T>,
        handler: (T, MutableList<String>) -> IngestOutcome,
    ): IngestSummaryDto {
        val errors = mutableListOf<String>()
        var accepted = 0
        var rejected = 0
        var duplicates = 0
        records.forEach { record ->
            when (handler(record, errors)) {
                IngestOutcome.ACCEPTED -> accepted++
                IngestOutcome.REJECTED -> rejected++
                IngestOutcome.DUPLICATE -> duplicates++
            }
        }
        return IngestSummaryDto(accepted, rejected, duplicates, errors)
    }

    private enum class IngestOutcome {
        ACCEPTED,
        REJECTED,
        DUPLICATE,
    }
}
