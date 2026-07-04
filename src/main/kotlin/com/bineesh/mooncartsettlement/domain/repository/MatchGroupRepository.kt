package com.bineesh.mooncartsettlement.domain.repository

import com.bineesh.mooncartsettlement.domain.model.MatchGroup
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MatchGroupRepository : JpaRepository<MatchGroup, UUID> {
    fun findByReconciliationRunId(runId: UUID): List<MatchGroup>
}
