package com.bineesh.mooncartsettlement.domain.repository

import com.bineesh.mooncartsettlement.domain.model.BankSettlement
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BankSettlementRepository : JpaRepository<BankSettlement, UUID> {
    fun findByReconciliationRunId(runId: UUID): List<BankSettlement>

    fun existsByReconciliationRunIdAndBankReferenceNumber(runId: UUID, bankReferenceNumber: String): Boolean
}
