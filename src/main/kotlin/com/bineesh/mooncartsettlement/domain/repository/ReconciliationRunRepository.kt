package com.bineesh.mooncartsettlement.domain.repository

import com.bineesh.mooncartsettlement.domain.model.ReconciliationRun
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReconciliationRunRepository : JpaRepository<ReconciliationRun, UUID>
