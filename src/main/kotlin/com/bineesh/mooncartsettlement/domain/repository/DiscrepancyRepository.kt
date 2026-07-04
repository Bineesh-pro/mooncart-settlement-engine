package com.bineesh.mooncartsettlement.domain.repository

import com.bineesh.mooncartsettlement.domain.enums.DiscrepancyType
import com.bineesh.mooncartsettlement.domain.model.Discrepancy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface DiscrepancyRepository : JpaRepository<Discrepancy, UUID>, JpaSpecificationExecutor<Discrepancy> {
    fun findByReconciliationRunId(runId: UUID): List<Discrepancy>

    fun countByReconciliationRunIdAndType(runId: UUID, type: DiscrepancyType): Long
}
