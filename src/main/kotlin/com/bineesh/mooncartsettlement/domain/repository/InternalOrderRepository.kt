package com.bineesh.mooncartsettlement.domain.repository

import com.bineesh.mooncartsettlement.domain.model.InternalOrder
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InternalOrderRepository : JpaRepository<InternalOrder, UUID> {
    fun findByReconciliationRunId(runId: UUID): List<InternalOrder>

    fun existsByReconciliationRunIdAndOrderId(runId: UUID, orderId: String): Boolean
}
