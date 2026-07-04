package com.bineesh.mooncartsettlement.domain.repository

import com.bineesh.mooncartsettlement.domain.model.YunoTransaction
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface YunoTransactionRepository : JpaRepository<YunoTransaction, UUID> {
    fun findByReconciliationRunId(runId: UUID): List<YunoTransaction>

    fun findByReconciliationRunIdAndYunoTransactionId(runId: UUID, yunoTransactionId: String): YunoTransaction?

    fun existsByReconciliationRunIdAndYunoTransactionId(runId: UUID, yunoTransactionId: String): Boolean
}
